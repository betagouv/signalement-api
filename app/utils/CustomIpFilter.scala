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

  private val blackListedIps = rawBlackListedIps.map(blackListIp => InetAddress.getByName(blackListIp.ip).getAddress)

  logger.debug(s"Black listed ips : $rawBlackListedIps")

  @inline def allowIP(req: RequestHeader): Boolean =
    if (blackListedIps.isEmpty) true
    else blackListedIps.forall(!JArrays.equals(_, req.connection.remoteAddress.getAddress))

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] =
    if (allowIP(requestHeader)) {
      logger.debug(s"IP ${requestHeader.remoteAddress} is not blacklisted.")
      nextFilter(requestHeader)
    } else {
      logger.warn(s"Access denied to ${requestHeader.path} for IP ${requestHeader.remoteAddress}.")
      Future.successful(Results.Forbidden)
    }
}
