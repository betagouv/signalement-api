package utils

import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results
import repositories.ipblacklist.IpBlackListRepositoryInterface

import java.net.InetAddress
import java.util.{Arrays => JArrays}
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.Future
import scala.util.Try

class CustomIpFilter(ipBlackListRepository: IpBlackListRepositoryInterface)(implicit val mat: Materializer)
    extends Filter {

  private val logger = Logger(getClass)

  private val rawBlackListedIps = Try(Await.result(ipBlackListRepository.list(), 1.second)).getOrElse(Seq.empty)

  private val blackListedIps =
    rawBlackListedIps.map(blackListIp => InetAddress.getByName(blackListIp.ip).getAddress -> blackListIp.critical)

  logger.debug(s"Black listed ips : $rawBlackListedIps")

  @inline private def allowIP(req: RequestHeader): Boolean =
    blackListedIps.forall(t => !JArrays.equals(t._1, req.connection.remoteAddress.getAddress))

  @inline private def isCritical(req: RequestHeader): Boolean =
    blackListedIps.exists { case (ip, critical) =>
      critical && JArrays.equals(ip, req.connection.remoteAddress.getAddress)
    }

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] =
    if (allowIP(requestHeader)) {
      logger.debug(s"IP ${requestHeader.remoteAddress} is not blacklisted.")
      nextFilter(requestHeader)
    } else {
      logger.info(s"Access denied to ${requestHeader.path} for IP ${requestHeader.remoteAddress}.")
      if (isCritical(requestHeader)) {
        logger.warn(s"A critical ip tried to connect and has been rejected: ${requestHeader.remoteAddress}")
      }
      Future.successful(Results.Forbidden)
    }
}
