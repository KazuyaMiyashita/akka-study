package akka09

import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.NotUsed

object Main extends App {

  def apply(): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      context.spawn(GenericResponseWrapper(), "generec_response_wrapper")
      context.spawn(SendFutureResultToSelf(), "send_future_result_to_self")

      // context.spawn(GeneralPurposeResponseAggregator(), "general_purpose_response_aggregator")

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
