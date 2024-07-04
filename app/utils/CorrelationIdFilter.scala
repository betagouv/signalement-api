package utils

import org.apache.pekko.stream.Materializer
import org.slf4j.MDC
import play.api.Logging
import play.api.MarkerContext
import play.api.mvc._
import utils.CorrelationIdFilter.CorrelationIdHeader
import utils.CorrelationIdFilter.CorrelationIdLabel

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CorrelationIdFilter(implicit
    val mat: Materializer,
    ec: ExecutionContext
) extends Filter
    with Logging {

  def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val correlationId = requestHeader.headers.get(CorrelationIdHeader).getOrElse(UUID.randomUUID().toString)
    MDC.put(CorrelationIdLabel, correlationId)
    val newHeaders       = requestHeader.headers.add(CorrelationIdHeader -> correlationId)
    val newRequestHeader = requestHeader.withHeaders(newHeaders)

    val t = implicitly[MarkerContext]

    logger.info("toto")(t)

    nextFilter(newRequestHeader)
      .map { result =>
        MDC.remove(CorrelationIdLabel)
        result.withHeaders(CorrelationIdHeader -> correlationId)
      }(ec)
      .recover { case e: Throwable =>
        MDC.remove(CorrelationIdLabel)
        throw e
      }(ec)
  }
}

object CorrelationIdFilter {
  val CorrelationIdHeader = "X-Correlation-ID"
  val CorrelationIdLabel  = "correlationId"
}
