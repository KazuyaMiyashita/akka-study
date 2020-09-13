package akka14

import scala.util.Try
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.pattern

object Delay {

  sealed trait Request[T]
  final case class RequestMessage[T](
      message: T,
      delay: FiniteDuration,
      replyTo: ActorRef[Try[T]]
  ) extends Request[T]
  private final case class InternalAdapter[T](status: Try[T], replyTo: ActorRef[Try[T]]) extends Request[T]

  def apply[T](): Behavior[Request[T]] = Behaviors.receive { (context, message) =>
    implicit val classicSystem = context.system.classicSystem

    message match {
      case r: RequestMessage[T] => {
        val delayed: Future[RequestMessage[T]] = pattern.after(r.delay)(Future.successful(r))
        context.pipeToSelf(delayed) {
          case t => InternalAdapter(t.map(_.message), r.replyTo)
        }
      }
      case InternalAdapter(status, replyTo) => {
        replyTo ! status
      }
    }
    Behaviors.same
  }

}
