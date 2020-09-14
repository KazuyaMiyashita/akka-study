package akka14

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Source, Flow, Sink}
import akka.stream.typed.scaladsl.{ActorFlow, ActorSource}
import akka.http.scaladsl.model.{HttpResponse, HttpRequest, Uri}
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.AttributeKeys.webSocketUpgrade
import akka.http.scaladsl.model.ws.Message
import akka.util.Timeout
import akka.NotUsed
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser

import ChatRoomsAdapter._
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.OverflowStrategy
import akka14.ChatRooms.ChatRoomCommand
import akka.http.scaladsl.model.ws.BinaryMessage

class ChatRoomsAdapter(actor: ActorRef[ChatRooms.Command]) extends Handler {

  override def handleRequest: Flow[HttpRequest, HttpResponse, NotUsed] = {
    Flow[HttpRequest].flatMapConcat { r: HttpRequest =>
      val flow = r match {
        case req @ HttpRequest(GET, Uri.Path("/chat"), _, _, _)  => listRooms
        case req @ HttpRequest(POST, Uri.Path("/chat"), _, _, _) => createRoom
        case req @ HttpRequest(DELETE, Uri.Path(path), _, _, _)
            if """/chat/[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r.matches(path) =>
          deleteRoom
        case req @ HttpRequest(GET, Uri.Path(path), _, _, _)
            if """/chat/[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r.matches(path) =>
          fetchMessages
        case req @ HttpRequest(POST, Uri.Path(path), _, _, _)
            if """/chat/[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r.matches(path) =>
          addMessage
        case req @ HttpRequest(GET, Uri.Path(path), _, _, _)
            if """/chat/[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}/stream""".r.matches(
              path
            ) =>
          req.attribute(webSocketUpgrade) match {
            case Some(upgrade) =>
              Flow.fromFunction[HttpRequest, HttpResponse](req => upgrade.handleMessages(subscribeMessage(req)))
            case None =>
              Flow.fromFunction((_: HttpRequest) => HttpResponse(400, entity = "Not a valid websocket request!"))
          }
        // FIXME: handle other path to 404 error
      }
      Source.single(r).via(flow)
    }
  }

  private val listRooms: Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val timeout: Timeout = 1.seconds

    val flow: Flow[HttpRequest, List[ChatRooms.ChatRoomInfo], NotUsed] = ActorFlow.ask(parallelism = 8)(ref = actor)(
      makeMessage = (_, ref) => ChatRooms.ListRooms(ref)
    )

    val responseMapping: Flow[List[ChatRooms.ChatRoomInfo], HttpResponse, NotUsed] =
      Flow[List[ChatRooms.ChatRoomInfo]].map { chatRoomInfos =>
        val roomResponses =
          chatRoomInfos.map(chatRoomInfo => RoomResponse(chatRoomInfo.id.asString, chatRoomInfo.roomName))
        val responseString = encodeListRoomResponse(roomResponses)
        HttpResponse(200, entity = responseString)
      }

    flow.via(responseMapping)
  }

  private val createRoom: Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val timeout: Timeout = 1.seconds

    val request: Flow[HttpRequest, Either[HttpResponse, CreateRoomRequest], NotUsed] = Flow[HttpRequest].flatMapConcat {
      r: HttpRequest => r.entity.dataBytes.map(_.decodeString(StandardCharsets.UTF_8)).map(decodeCreateRoomRequest)
    }

    val flow: Flow[CreateRoomRequest, ChatRooms.ChatRoomInfo, NotUsed] = ActorFlow.ask(parallelism = 8)(ref = actor)(
      makeMessage = (message, ref) => ChatRooms.CreateRoom(message.name, ref)
    )

    val responseMapping: Flow[ChatRooms.ChatRoomInfo, HttpResponse, NotUsed] = Flow[ChatRooms.ChatRoomInfo].map {
      chatRoomInfo =>
        val response       = RoomResponse(chatRoomInfo.id.asString, chatRoomInfo.roomName)
        val responseString = encodeCreateRoomResponse(response)
        HttpResponse(200, entity = responseString)
    }

    request
      .flatMapConcat { r: Either[HttpResponse, CreateRoomRequest] =>
        r match {
          case Right(value) => Source.single(value).via(flow).via(responseMapping).map(Right(_))
          case Left(value)  => Source.single(Left(value))
        }
      }
      .map(_.merge)
  }

  private val deleteRoom: Flow[HttpRequest, HttpResponse, NotUsed] = {

    val chatRoomId: Flow[HttpRequest, ChatRooms.ChatRoomId, NotUsed] = Flow[HttpRequest].map { r =>
      ChatRooms.ChatRoomId.fromString(r.uri.path.toString.drop("/chat/".length)) // FIXME refactor
    }

    val flow: Flow[ChatRooms.ChatRoomId, HttpResponse, NotUsed] = Flow.fromFunction { chatRoomId =>
      actor ! ChatRooms.DeleteRoom(chatRoomId)
      HttpResponse(202)
    }

    chatRoomId.via(flow)
  }

  private val fetchMessages: Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val timeout: Timeout = 1.seconds

    val chatRoomId: Flow[HttpRequest, ChatRooms.ChatRoomId, NotUsed] = Flow[HttpRequest].map { r =>
      ChatRooms.ChatRoomId.fromString(r.uri.path.toString.drop("/chat/".length)) // FIXME refactor
    }

    val flow: Flow[ChatRooms.ChatRoomId, List[ChatRoom.Message], NotUsed] = ActorFlow.ask(parallelism = 8)(ref = actor)(
      makeMessage = (chatRoomId, ref) => ChatRooms.ChatRoomCommand(chatRoomId, ChatRoom.FetchMessages(ref))
    )

    val responseMapping: Flow[List[ChatRoom.Message], HttpResponse, NotUsed] =
      Flow[List[ChatRoom.Message]].map { messages =>
        val messageResponse =
          messages.map(m => MessageResponse(m.body))
        val responseString = encodeFetchMessageResponse(messageResponse)
        HttpResponse(200, entity = responseString)
      }

    chatRoomId.via(flow).via(responseMapping)
  }

  private val addMessage: Flow[HttpRequest, HttpResponse, NotUsed] = {
    val request: Flow[HttpRequest, Either[HttpResponse, (ChatRooms.ChatRoomId, AddMessageRequest)], NotUsed] =
      Flow[HttpRequest].flatMapConcat { r =>
        val chatRoomId = ChatRooms.ChatRoomId.fromString(r.uri.path.toString.drop("/chat/".length)) // FIXME refactor
        val addMessageRequest: Source[Either[HttpResponse, AddMessageRequest], _] =
          r.entity.dataBytes.map(_.decodeString(StandardCharsets.UTF_8)).map(decodeAddMessageRequest)
        addMessageRequest.flatMapConcat {
          case Right(value) => Source.single(Right((chatRoomId, value)))
          case Left(value)  => Source.single(Left(value))
        }
      }

    val flow: Flow[(ChatRooms.ChatRoomId, AddMessageRequest), HttpResponse, NotUsed] = Flow.fromFunction {
      case (chatRoomId, request) =>
        actor ! ChatRooms.ChatRoomCommand(chatRoomId, ChatRoom.AddMessage(ChatRoom.Message(request.body)))
        HttpResponse(202)
    }

    request.flatMapConcat {
      case Right(value) => Source.single(value).via(flow)
      case Left(value)  => Source.single(value)
    }
  }

  private val subscribeMessage: HttpRequest => Flow[Message, Message, NotUsed] = { request =>
    val chatRoomId = ChatRooms.ChatRoomId.fromString(request.uri.path.toString.drop("/chat/".length).take(36)) // FIXME refactor

    val source = ActorSource
      .actorRef[ChatRoom.Message](
        completionMatcher = PartialFunction.empty,
        failureMatcher = PartialFunction.empty,
        bufferSize = 8,
        overflowStrategy = OverflowStrategy.fail
      )
      .mapMaterializedValue { subscribeTo =>
        actor ! ChatRoomCommand(chatRoomId, ChatRoom.SubscribeMessage(subscribeTo))
      }

    val responseMapping: Flow[ChatRoom.Message, TextMessage, NotUsed] = Flow.fromFunction { message =>
      TextMessage(encodeMessageResponse(MessageResponse(message.body)))
    }

    val requestMapping: Flow[Message, ChatRoom.Message, NotUsed] = Flow[Message]
      .flatMapConcat {
        case tm: TextMessage   => tm.textStream
        case bm: BinaryMessage => Source.empty
      }
      .map(body => ChatRoom.Message(body))

    val addMessage: Sink[ChatRoom.Message, NotUsed] = Sink
      .foreach[ChatRoom.Message] { message => actor ! ChatRoomCommand(chatRoomId, ChatRoom.AddMessage(message)) }
      .mapMaterializedValue(_ => NotUsed)

    Flow.fromSinkAndSource(
      requestMapping.to(addMessage),
      source.via(responseMapping)
    )
  }

}

object ChatRoomsAdapter {

  case class CreateRoomRequest(name: String)
  case class RoomResponse(chatRoomId: String, name: String)
  private val createRoomDecoder: Decoder[CreateRoomRequest] = deriveDecoder
  def decodeCreateRoomRequest(in: String): Either[HttpResponse, CreateRoomRequest] = {
    parser.parse(in).flatMap(createRoomDecoder.decodeJson).left.map { _ =>
      HttpResponse(400, entity = "bad request. invalid json.")
    }
  }
  private val roomEncoder: Encoder[RoomResponse]          = deriveEncoder
  def encodeCreateRoomResponse(res: RoomResponse): String = roomEncoder(res).noSpaces
  def encodeListRoomResponse(res: List[RoomResponse]): String = {
    import io.circe.syntax._
    implicit val encoder = roomEncoder
    res.asJson.noSpaces
  }
  case class AddMessageRequest(body: String)
  private val addMessageDecoder: Decoder[AddMessageRequest] = deriveDecoder
  def decodeAddMessageRequest(in: String): Either[HttpResponse, AddMessageRequest] = {
    parser.parse(in).flatMap(addMessageDecoder.decodeJson).left.map { _ =>
      HttpResponse(400, entity = "bad request. invalid json.")
    }
  }
  case class MessageResponse(body: String)
  private val messageEncoder: Encoder[MessageResponse] = deriveEncoder
  def encodeMessageResponse(res: MessageResponse): String = {
    import io.circe.syntax._
    implicit val encoder = messageEncoder
    res.asJson.noSpaces
  }
  def encodeFetchMessageResponse(res: List[MessageResponse]): String = {
    import io.circe.syntax._
    implicit val encoder = messageEncoder
    res.asJson.noSpaces
  }

}
