package akka08

import scala.util.{Success, Failure}
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

object Main extends App {

  implicit val cookieFabricSystem: ActorSystem[CookieFabric.GimeMeCookies] = ActorSystem(CookieFabric(), "main")

  implicit val timeout: Timeout = 3.seconds
  implicit val ec               = cookieFabricSystem.executionContext

  val result: Future[CookieFabric.Reply] = cookieFabricSystem.ask(ref => CookieFabric.GimeMeCookies(3, ref))

  result.onComplete {
    case Success(CookieFabric.Cookies(count))         => println(s"Yay, $count cookies!")
    case Success(CookieFabric.InvalidRequest(reason)) => println(s"No cookies for me. $reason")
    case Failure(e)                                   => println(s"Boo! didn't get cookies: ${e.getMessage}")
  }

  println("\npress ENTER to terminate.\n")

  scala.io.StdIn.readLine()
  cookieFabricSystem.terminate()

}
