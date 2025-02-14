package utils

import org.apache.pekko.stream.Materializer
import play.api.Logger
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results
import repositories.ipblacklist.IpBlackListRepositoryInterface

import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.{Arrays => JArrays}
import scala.concurrent.duration.DurationInt
import scala.concurrent.Await
import scala.concurrent.Future
import scala.util.Try

class CustomIpFilter(ipBlackListRepository: IpBlackListRepositoryInterface)(implicit val mat: Materializer)
    extends Filter {

  private val logger = Logger(getClass)

  private lazy val rawBlackListedIps = Try(Await.result(ipBlackListRepository.list(), 1.second)).getOrElse(Seq.empty)

  private lazy val blackListedIps =
    rawBlackListedIps
      .filter(rawIp => !rawIp.ip.contains("/"))
      .map(blackListIp => InetAddress.getByName(blackListIp.ip).getAddress -> blackListIp.critical)

  private lazy val blackListedSubnets =
    rawBlackListedIps.filter(_.ip.contains("/")).map { blackListIp =>
      val Array(ip, prefix) = blackListIp.ip.split("/")
      (InetAddress.getByName(ip).getAddress, prefix.toInt)
    }

  @inline private def isCritical(req: RequestHeader): Boolean = {
    val ipBytes = req.connection.remoteAddress.getAddress
    blackListedIps.exists { case (ip, critical) =>
      critical && JArrays.equals(ip, ipBytes)
    }
  }

  @inline private def isInSubnet(ipBytes: Array[Byte], subnetBytes: Array[Byte], prefixLength: Int): Boolean = {
    val mask          = 0xffffffff << (32 - prefixLength)
    val ipAddress     = ByteBuffer.wrap(ipBytes).getInt
    val subnetAddress = ByteBuffer.wrap(subnetBytes).getInt
    (ipAddress & mask) == (subnetAddress & mask)
  }

  @inline private[utils] def allowIP(req: RequestHeader): Boolean = {
    val ipBytes = req.connection.remoteAddress.getAddress
    blackListedIps.forall(t => !JArrays.equals(t._1, ipBytes)) &&
    blackListedSubnets.forall { case (subnet, prefixLength) =>
      !isInSubnet(ipBytes, subnet, prefixLength)
    }
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
