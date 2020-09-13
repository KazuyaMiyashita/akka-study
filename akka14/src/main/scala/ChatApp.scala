package akka14

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Source, Flow}
import akka.stream.typed.scaladsl.ActorFlow
import akka.http.scaladsl.model.{HttpResponse, HttpRequest, Uri}
import akka.http.scaladsl.model.HttpMethods._
import akka.util.Timeout
import akka.NotUsed
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser

import ChatRoomsAdapter._

class ChatRoomsAdapter(actor: ActorRef[ChatRooms.Command]) extends Handler {

  override def handleRequest: Flow[HttpRequest, HttpResponse, NotUsed] = {
    Flow[HttpRequest].flatMapConcat { r: HttpRequest =>
      val flow = r match {
        case req @ HttpRequest(GET, Uri.Path("/chat"), _, _, _)  => listRooms
        case req @ HttpRequest(POST, Uri.Path("/chat"), _, _, _) => createRoom
        case req @ HttpRequest(DELETE, Uri.Path(path), _, _, _)
            if """/chat/[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r.matches(path) =>
          deleteRoom
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

}

object ChatRoomsAdapter {

  case class CreateRoomRequest(name: String)
  case class RoomResponse(chatRoomId: String, name: String)
  private val createRoomDecoder: Decoder[CreateRoomRequest] = deriveDecoder[CreateRoomRequest]
  def decodeCreateRoomRequest(in: String): Either[HttpResponse, CreateRoomRequest] = {
    parser.parse(in).flatMap(createRoomDecoder.decodeJson).left.map { _ =>
      HttpResponse(400, entity = "bad request. invalid json.")
    }
  }
  private val roomEncoder: Encoder[RoomResponse]          = deriveEncoder[RoomResponse]
  def encodeCreateRoomResponse(res: RoomResponse): String = roomEncoder(res).noSpaces
  def encodeListRoomResponse(res: List[RoomResponse]): String = {
    import io.circe.syntax._
    implicit val encoder = roomEncoder
    res.asJson.noSpaces
  }

}
