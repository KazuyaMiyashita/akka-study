package akka11

import scala.util.{Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

object Main extends App {

  implicit val delayEchoActorSystem = ActorSystem(DelayEcho(), "main")

  implicit val timeout: Timeout = 3.seconds
  implicit val ec               = delayEchoActorSystem.executionContext

  val ExitMessage = ":exit"

  println(s"\npress messages for echo server. press $ExitMessage to terminate.\n")

  def loop(): Unit = {
    print("> ")
    val in = scala.io.StdIn.readLine()
    if (in == ExitMessage) {
      delayEchoActorSystem ! DelayEcho.Stop
    } else {
      val result: Future[String] = delayEchoActorSystem.askWithStatus(DelayEcho.RequestMessage(in, _))
      result.onComplete {
        case Success(value)     => println("echo: value")
        case Failure(exception) => println(s"error: ${exception.getMessage}")
      }
      loop()
    }
  }

  loop()

}
