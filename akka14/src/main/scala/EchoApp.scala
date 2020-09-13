package akka14

import scala.util.Try
import scala.concurrent.duration._
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.Flow
import akka.stream.typed.scaladsl.ActorFlow
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.util.Timeout
import akka.NotUsed
import java.nio.charset.StandardCharsets
import scala.util.Success
import scala.util.Failure

object EchoAppActor {

  sealed trait Command
  final case class Request(message: String, replyTo: ActorRef[Try[String]]) extends Command
  private final case class WrappedDelayResponse(
      response: Try[String],
      replyTo: ActorRef[Try[String]]
  ) extends Command

  val DelayMillis = 1.seconds

  def apply(): Behavior[Command] = Behaviors.setup { context =>
    val delay = context.spawnAnonymous(Delay[String]())

    Behaviors.receiveMessage {
      case Request(message, replyTo) => {
        delay ! Delay.RequestMessage[String](
          message,
          DelayMillis,
          context.messageAdapter(WrappedDelayResponse(_, replyTo))
        )
        Behaviors.same
      }
      case WrappedDelayResponse(response, replyTo) => {
        replyTo ! response
        Behaviors.same
      }
    }
  }

}

class EchoAppAdapter(actor: ActorRef[EchoAppActor.Command]) extends Handler {

  override def handleRequest: Flow[HttpRequest, HttpResponse, NotUsed] = {
    implicit val timeout: Timeout = 3.seconds

    val message: Flow[HttpRequest, String, NotUsed] = Flow[HttpRequest].flatMapConcat { r: HttpRequest =>
      r.entity.dataBytes.map(_.decodeString(StandardCharsets.UTF_8))
    }

    val flow: Flow[String, Try[String], NotUsed] = ActorFlow.ask(parallelism = 8)(ref = actor)(
      makeMessage = (message, ref) => EchoAppActor.Request(message, ref)
    )

    val responseMapping: Flow[Try[String], HttpResponse, NotUsed] = Flow[Try[String]].map {
      case Success(value) => HttpResponse(200, entity = value)
      case Failure(e)     => HttpResponse(500, entity = e.getMessage)
    }

    message.via(flow).via(responseMapping)
  }

}
