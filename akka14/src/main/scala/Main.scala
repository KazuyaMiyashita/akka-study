package akka14

import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.NotUsed

object Main extends App {

  def apply(): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      val echoAppActor = context.spawn(EchoAppActor(), "echo_app_actor")
      val chatAppActor = context.spawn(ChatRooms(), "chat_rooms")
      val webApp = new WebApp(
        echoApp = new EchoAppAdapter(echoAppActor),
        chatApp = new ChatRoomsAdapter(chatAppActor)
      )
      val routes = new RoutesImpl(webApp)

      context.spawn(WebServer(routes), "web")

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          Behaviors.stopped
      }
    }
  }

  val system = ActorSystem(Main(), "main")

  println("\npress ENTER to terminate.\n")

  scala.io.StdIn.readLine()
  system.terminate()

}

object Printer {

  def apply[T](): Behavior[T] = Behaviors.receive[T] { (context, message) =>
    context.log.info(message.toString)
    Behaviors.same
  }

}
