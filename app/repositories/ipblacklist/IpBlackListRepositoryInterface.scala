package repositories.ipblacklist

import scala.concurrent.Future

trait IpBlackListRepositoryInterface {
  def create(ip: BlackListedIp): Future[BlackListedIp]
  def delete(ip: String): Future[Int]
  def list(): Future[List[BlackListedIp]]
}
