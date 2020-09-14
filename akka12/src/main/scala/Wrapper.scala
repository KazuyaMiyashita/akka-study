package akka12

import scala.util.{Try, Success, Failure}
import scala.concurrent.duration._

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern.StatusReply

object Wrapper {

  sealed trait Request
  final case class RequestMessage(message: String, replyTo: ActorRef[StatusReply[String]]) extends Request
  private final case class WrappedAfterResponse(
      response: Try[String],
      replyTo: ActorRef[StatusReply[String]]
  ) extends Request

  val DelayMillis = 1.seconds

  def apply(): Behavior[Request] = Behaviors.setup { context =>
    val after = context.spawn(Delay[String](), "after")

    Behaviors.receiveMessage {
      case RequestMessage(message, replyTo) => {
        after ! Delay.RequestMessage[String](
          message,
          DelayMillis,
          replyTo
        )
        Behaviors.same
      }
    }
  }

}
