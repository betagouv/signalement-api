package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import models._

@Singleton
class CompanyAccessRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel(_))

  class CompanyAccessTable(tag: Tag) extends Table[CompanyAccess](tag, "company_accesses") {
    def companyId = column[UUID]("company_id")
    def userId = column[UUID]("user_id")
    def level = column[AccessLevel]("level")
    def updateDate = column[OffsetDateTime]("update_date")
    def pk = primaryKey("pk_company_user", (companyId, userId))
    def * = (companyId, userId, level, updateDate) <> (CompanyAccess.tupled, CompanyAccess.unapply)
  }

  val companyAccessTableQuery = TableQuery[CompanyAccessTable]

  def getAccessLevel(company: Company, user: User): Future[AccessLevel] = {
    Future(AccessLevel.NONE)
  }
  def setAccessLevel(company: Company, user: User, level: AccessLevel): Future[Unit] = {
    Future(Unit)
  }
}
