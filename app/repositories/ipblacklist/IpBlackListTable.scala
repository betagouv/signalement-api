package repositories.ipblacklist

import slick.lifted.ProvenShape
import slick.lifted.Tag
import repositories.PostgresProfile.api._

class IpBlackListTable(tag: Tag) extends Table[BlackListedIp](tag, "ip_black_list") {

  def ip       = column[String]("ip", O.PrimaryKey)
  def comment  = column[String]("comment")
  def critical = column[Boolean]("critical")

  override def * : ProvenShape[BlackListedIp] = (
    ip,
    comment,
    critical
  ) <> ((BlackListedIp.apply _).tupled, BlackListedIp.unapply)
}

object IpBlackListTable {
  val table = TableQuery[IpBlackListTable]
}
