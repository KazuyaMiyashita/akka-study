package akka14

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpResponse, Uri, HttpRequest}
import akka.stream.scaladsl.{Source, Flow, Sink}
import akka.NotUsed
import akka.stream.Materializer

trait Routes {
  def route(implicit mat: Materializer): Flow[HttpRequest, HttpResponse, NotUsed]
}

class RoutesImpl(webApp: WebApp) extends Routes {

  override def route(implicit mat: Materializer): Flow[HttpRequest, HttpResponse, NotUsed] =
    Flow[HttpRequest].flatMapConcat { r: HttpRequest =>
      {
        val flow = r match {
          case req @ HttpRequest(POST, Uri.Path("/"), _, _, _)                           => webApp.echoApp.handleRequest
          case req @ HttpRequest(_, Uri.Path(path), _, _, _) if path.startsWith("/chat") => webApp.chatApp.handleRequest
          case req @ _ => {
            r.discardEntityBytes()
            Flow.fromSinkAndSource(
              sink = Sink.ignore,
              source = Source.single(HttpResponse(404, entity = "Unknown resource!"))
            )
          }
        }
        Source.single(r).via(flow)
      }
    }

}
