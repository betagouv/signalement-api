package utils

import akka.stream.Materializer
import play.api.Logging
import play.api.mvc._
import utils.Logs.RichLogger
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class LoggingFilter(implicit val mat: Materializer, ec: ExecutionContext) extends Filter with Logging {

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val startTime = System.currentTimeMillis
    logger.infoWithTitle(
      "req",
      s"${requestHeader.method} ${requestHeader.uri}"
    )
    nextFilter(requestHeader).map { res =>
      val endTime = System.currentTimeMillis
      val duration = endTime - startTime
      logger.infoWithTitle(
        "res",
        s"${requestHeader.method} ${requestHeader.uri} - ${res.header.status} ${duration}ms"
      )
      res
    }
  }

}
