package utils

import org.apache.pekko.stream.Materializer
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
      s"${requestHeader.remoteAddress} - ${requestHeader.method} ${requestHeader.uri}"
    )
    nextFilter(requestHeader).map { res =>
      val endTime  = System.currentTimeMillis
      val duration = endTime - startTime

//      val correlationId = requestHeader.headers.get(CorrelationIdHeader)
//      correlationId.foreach(MDC.put(CorrelationIdLabel, _))
      logger.infoWithTitle(
        "res",
        s"${requestHeader.remoteAddress} - ${requestHeader.method} ${requestHeader.uri} - ${res.header.status} ${duration}ms"
      )
//      correlationId.foreach(_ => MDC.remove(CorrelationIdLabel))
      res
    }
  }

}
