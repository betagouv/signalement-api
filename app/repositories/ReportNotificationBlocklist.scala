package repositories

import models._
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import utils.Constants.ReportStatus.ReportStatusValue
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportNotificationBlocklistTable(tag: Tag)
    extends Table[ReportNotificationBlocklist](tag, "report_notification_blocklist") {
  def userId = column[UUID]("user_id")
  def companyId = column[UUID]("company_id")
  def dateCreation = column[OffsetDateTime]("date_creation")

  def company = foreignKey("fk_report_notification_blocklist_user", companyId, CompanyTables.tables)(
    _.id,
    onDelete = ForeignKeyAction.Cascade
  )
  def user = foreignKey("fk_report_notification_blocklist_user", userId, UserTables.tables)(
    _.id,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (
    userId,
    companyId,
    dateCreation
  ) <> ((ReportNotificationBlocklist.apply _).tupled, ReportNotificationBlocklist.unapply)
}

object ReportNotificationBlocklistTables {
  val tables = TableQuery[ReportNotificationBlocklistTable]
}

@Singleton
class ReportNotificationBlocklistRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  val query = ReportNotificationBlocklistTables.tables
  val queryUser = UserTables.tables

  def findByUserId(userId: UUID): Future[Seq[ReportNotificationBlocklist]] =
    db.run(query.filter(_.userId === userId).result)

  def filterBlockedEmails(email: List[EmailAddress], companyId: UUID): Future[List[EmailAddress]] =
    db.run(
      queryUser
        .filter(u =>
          u.acceptNotifications === false || (u.id in (query.filter(_.companyId === companyId).map(_.userId)))
        )
        .map(_.email)
        .to[List]
        .result
    ).map(blockedEmails => email.diff(blockedEmails))

  def create(userId: UUID, companyId: UUID): Future[ReportNotificationBlocklist] = {
    val entity = ReportNotificationBlocklist(userId = userId, companyId = companyId)
    db.run(query += entity).map(_ => entity)
  }

  def delete(userId: UUID, companyId: UUID): Future[Int] =
    db.run(query.filter(_.userId === userId).filter(_.companyId === companyId).delete)
}
