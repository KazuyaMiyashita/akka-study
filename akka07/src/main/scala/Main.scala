package akka07

import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.NotUsed

object Main extends App {

  def apply(): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      context.spawn(FireAndForget(), "fire_and_forget")
      context.spawn(RequestResponse(), "request_response")

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          Behaviors.stopped
      }
    }
  }

  val system = ActorSystem(Main(), "main")

  println("\npress ENTER to terminate.\n")

  io.StdIn.readLine()
  system.terminate()

}
