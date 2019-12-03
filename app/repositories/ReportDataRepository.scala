package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.ReportData
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.Constants.ActionEvent

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportDataRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     val eventRepository: EventRepository,
                                     val reportRepository: ReportRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  class ReportDataTable(tag: Tag) extends Table[ReportData](tag, "reportData") {
    def reportId = column[UUID]("report_id", O.PrimaryKey)
    def readTime = column[Option[Long]]("read_time")
    def responseTime = column[Option[Long]]("reponse_time")

    def * = (reportId, readTime, responseTime) <> (ReportData.tupled, ReportData.unapply)
  }

  private val reportDataTableQuery = TableQuery[ReportDataTable]

  def updateReportReadTime = {

    val eventsWithoutReadTime = eventRepository.eventTableQuery
      .filter(_.action === ActionEvent.ENVOI_SIGNALEMENT.value)
      .joinLeft(reportDataTableQuery).on(_.reportId === _.reportId)
      .filter(_._2.map(!_.readTime.isDefined).getOrElse(true))
      .join(reportRepository.reportTableQuery)
      .map(result => (result._2.id, result._1._1.creationDate - result._2.creationDate))

    //db.run(reportDataTableQuery.insertOrUpdateAll(eventsWithoutReadTime.map(e => (e._1, Some(e._2), None))))
  }
}
