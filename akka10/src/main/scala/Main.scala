package akka10

import scala.concurrent.duration._

import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.NotUsed

object Main extends App {

  def apply(): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      val printer = context.spawn(Printer[String](), "printer")

      val after  = context.spawn(After[String](), "after")
      val afters = context.spawn(Afters[String](), "afters")

      (0 to 5000 by 1000).map(_.milli).foreach { d =>
        after ! After.RequestMessage(s"after ${d.toMillis} [ms]", d, printer)
      }

      val durations = (0 to 5000 by 100).map(_.milli)
      afters ! Afters.RequestMessage("yes", durations, printer)

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
