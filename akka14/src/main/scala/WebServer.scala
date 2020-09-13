package akka14

import scala.concurrent.Future
import akka.actor.typed.{Behavior, Terminated}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{Sink, Source}
import akka.http.scaladsl.Http
import akka.NotUsed

object WebServer {

  def apply(routes: Routes): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      implicit val system = context.system.classicSystem
      implicit val ec     = system.dispatcher

      val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
        Http().newServerAt("localhost", 8080).connectionSource()
      val bindingFuture: Future[Http.ServerBinding] =
        serverSource
          .to(Sink.foreach { connection => // foreach materializes the source
            println("Accepted new connection from " + connection.remoteAddress)
            connection.handleWith(routes.route)
          })
          .run()

      Behaviors.receiveSignal {
        case (_, Terminated(_)) =>
          bindingFuture.flatMap(_.unbind())
          Behaviors.stopped
      }
    }
  }

}
