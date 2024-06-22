package repositories.ipblacklist

import repositories.TypedDatabaseTable
import slick.lifted.ProvenShape
import slick.lifted.Tag
import repositories.PostgresProfile.api._

class IpBlackListTable(tag: Tag) extends TypedDatabaseTable[BlackListedIp, String](tag, "ip_black_list") {

  def ip      = column[String]("ip")
  def comment = column[String]("comment")

  override def * : ProvenShape[BlackListedIp] = (
    ip,
    comment
  ) <> ((BlackListedIp.apply _).tupled, BlackListedIp.unapply)
}

object IpBlackListTable {
  val table = TableQuery[IpBlackListTable]
}
