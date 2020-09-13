package akka14

import akka.stream.scaladsl.Flow
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.NotUsed

trait Handler {

  def handleRequest: Flow[HttpRequest, HttpResponse, NotUsed]

}
