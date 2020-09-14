package akka12

import scala.util.{Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.duration._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import java.util.concurrent.{Executors, ExecutorService}

object Main extends App {

  val wrapperActorSystem = ActorSystem(Wrapper(), "main")
  val timeout: Timeout   = 3.seconds
  val scheduler          = wrapperActorSystem.scheduler

  val mainExecutors: ExecutorService = Executors.newFixedThreadPool(3)
  val mainExecutionContext           = ExecutionContext.fromExecutor(mainExecutors)

  println(s"\npress ENTER to terminate.\n")

  var active = true
  var count  = 0

  while (active) {
    count += 1
    val mes = s"ya $count"
    println(mes)
    val result: Future[String] = wrapperActorSystem.askWithStatus(Wrapper.RequestMessage(mes, _))(timeout, scheduler)
    result.onComplete {
      case Success(value) => println(s"echo: $value")
      case Failure(e)     => println(s"error: ${e.getMessage}")
    }(mainExecutionContext)
    Thread.sleep(2 * 100)
  }

  scala.io.StdIn.readLine()
  active = false
  wrapperActorSystem.terminate()

}
