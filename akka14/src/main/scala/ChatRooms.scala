package akka14

import java.util.UUID
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object ChatRooms {

  sealed trait Command
  final case class CreateRoom(roomName: String, replyTo: ActorRef[ChatRoomInfo])      extends Command
  final case class ListRooms(replyTo: ActorRef[List[ChatRoomInfo]])                   extends Command
  final case class DeleteRoom(chatRoomId: ChatRoomId)                                 extends Command
  final case class ChatRoomCommand(chatRoomId: ChatRoomId, command: ChatRoom.Command) extends Command

  case class ChatRoomId(value: UUID) {
    def asString: String = value.toString
  }
  object ChatRoomId {
    def create(): ChatRoomId   = ChatRoomId(UUID.randomUUID())
    def fromString(id: String) = ChatRoomId(UUID.fromString(id))
  }

  case class ChatRoomInfo(id: ChatRoomId, roomName: String, ref: ActorRef[ChatRoom.Command])

  def apply(): Behavior[Command] = chatRooms(Map.empty)

  def chatRooms(
      rooms: Map[ChatRoomId, ChatRoomInfo]
  ): Behavior[Command] = Behaviors.receive { (context, command) =>
    command match {
      case CreateRoom(roomName, replyTo) => {
        val chatRoomId   = ChatRoomId.create()
        val chatRoomRef  = context.spawnAnonymous(ChatRoom())
        val chatRoomInfo = ChatRoomInfo(chatRoomId, roomName, chatRoomRef)
        replyTo ! chatRoomInfo
        chatRooms(rooms + (chatRoomId -> chatRoomInfo))
      }
      case ListRooms(replyTo) => {
        replyTo ! rooms.values.toList
        Behaviors.same
      }
      case DeleteRoom(chatRoomId) => {
        chatRooms(rooms - chatRoomId)
      }
      case ChatRoomCommand(chatRoomId, command) => {
        rooms.get(chatRoomId).map(_.ref).foreach(_ ! command) // FIXME chatRoomIdが存在しなかった時の挙動
        Behaviors.same
      }
    }
  }

}
