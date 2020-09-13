package akka14

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ChatRoom {

  case class Message(body: String)

  sealed trait Command
  final case class AddMessage(message: Message)                       extends Command
  final case class FetchMessages(replyTo: ActorRef[List[Message]])    extends Command
  final case class SubscribeMessage(subscribeTo: ActorRef[Message])   extends Command
  final case class UnSubscribeMessage(subscribeTo: ActorRef[Message]) extends Command

  def apply(): Behavior[Command] = chatRoom(Nil, Set.empty)

  def chatRoom(
      messages: List[Message],
      subscribing: Set[ActorRef[Message]]
  ): Behavior[Command] = Behaviors.receive { (context, command) =>
    command match {
      case AddMessage(message) => {
        subscribing.foreach(_ ! message)
        chatRoom(message :: messages, subscribing)
      }
      case FetchMessages(replyTo) => {
        replyTo ! messages
        Behaviors.same
      }
      case SubscribeMessage(subscribeTo) => {
        chatRoom(messages, subscribing + subscribeTo)
      }
      case UnSubscribeMessage(subscribeTo) => {
        chatRoom(messages, subscribing - subscribeTo)
      }
    }
  }

}
