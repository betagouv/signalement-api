package repositories

import models.report.ReportBlockedNotification
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import repositories.company.CompanyTable
import repositories.user.UserTable
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportBlockedNotificationTable(tag: Tag)
    extends Table[ReportBlockedNotification](tag, "report_notifications_blocked") {
  def userId = column[UUID]("user_id")
  def companyId = column[UUID]("company_id")
  def dateCreation = column[OffsetDateTime]("date_creation")

  def company = foreignKey("fk_report_notification_blocklist_user", companyId, CompanyTable.table)(
    _.id,
    onDelete = ForeignKeyAction.Cascade
  )
  def user = foreignKey("fk_report_notification_blocklist_user", userId, UserTable.table)(
    _.id,
    onDelete = ForeignKeyAction.Cascade
  )

  def * = (
    userId,
    companyId,
    dateCreation
  ) <> ((ReportBlockedNotification.apply _).tupled, ReportBlockedNotification.unapply)
}

object ReportNotificationBlocklistTable {
  val table = TableQuery[ReportBlockedNotificationTable]
}

@Singleton
class ReportNotificationBlockedRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  val query = ReportNotificationBlocklistTable.table
  val queryUser = UserTable.table

  def findByUserId(userId: UUID): Future[Seq[ReportBlockedNotification]] =
    db.run(query.filter(_.userId === userId).result)

  def filterBlockedEmails(email: Seq[EmailAddress], companyId: UUID): Future[Seq[EmailAddress]] =
    db.run(
      queryUser
        .filter(_.id in (query.filter(_.companyId === companyId).map(_.userId)))
        .map(_.email)
        .to[List]
        .result
    ).map { blockedEmails =>
      email.diff(blockedEmails)
    }

  def create(userId: UUID, companyIds: Seq[UUID]): Future[Seq[ReportBlockedNotification]] = {
    val entities = companyIds.map(companyId => ReportBlockedNotification(userId = userId, companyId = companyId))
    db.run(query ++= entities).map(_ => entities)
  }

  def delete(userId: UUID, companyIds: Seq[UUID]): Future[Int] =
    db.run(query.filter(_.userId === userId).filter(_.companyId inSet companyIds).delete)
}
