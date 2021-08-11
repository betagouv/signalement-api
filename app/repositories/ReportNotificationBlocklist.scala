package repositories

import models._
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import utils.Constants.ReportStatus.ReportStatusValue

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}


class ReportNotificationBlocklistTable(tag: Tag) extends Table[ReportNotificationBlocklist](tag, "report_notification_blocklist") {
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

  def * = (userId, companyId, dateCreation) <> ((ReportNotificationBlocklist.apply _).tupled, ReportNotificationBlocklist.unapply)
}

object ReportNotificationBlocklistTables {
  val tables = TableQuery[ReportNotificationBlocklistTable]
}

@Singleton
class ReportNotificationBlocklistRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider,
)(implicit
  ec: ExecutionContext
) {
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  val query = ReportNotificationBlocklistTables.tables

  def create(userId: UUID, companyId: UUID): Future[Int] = {
    db.run(query += ReportNotificationBlocklist(
      userId = userId,
      companyId = companyId,
    ))
  }

  def findByUser(userId: UUID): Future[Seq[(ReportNotificationBlocklist, Company)]] = {
    val q = query.join(CompanyTables.tables).filter(_._1.userId === userId)
    db.run(q.result)
  }
}