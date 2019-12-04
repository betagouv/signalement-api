package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.ReportData
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.Constants.ActionEvent
import com.github.tminglei.slickpg.agg.PgAggFuncSupport.OrderedSetAggFunctions._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportDataRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     val eventRepository: EventRepository,
                                     val reportRepository: ReportRepository)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  class ReportDataTable(tag: Tag) extends Table[ReportData](tag, "report_data") {
    def reportId = column[UUID]("report_id", O.PrimaryKey)
    def readDelay = column[Option[Double]]("read_delay")
    def responseDelay = column[Option[Double]]("response_delay")

    def * = (reportId, readDelay, responseDelay) <> (ReportData.tupled, ReportData.unapply)
  }

  private val reportDataTableQuery = TableQuery[ReportDataTable]

  def updateReportReadDelay = {
    for {
      delaisToAdd <- db.run(
        eventRepository.eventTableQuery
          .filter(_.action === ActionEvent.ENVOI_SIGNALEMENT.value)
          .joinLeft(reportDataTableQuery).on(_.reportId === _.reportId)
          .filterNot(_._2.map(_.readDelay.isDefined).getOrElse(false))
          .join(reportRepository.reportTableQuery).on(_._1.reportId === _.id)
          .map(result => (result._1._1.reportId, result._1._1.creationDate - result._2.creationDate, result._1._2.flatMap(_.responseDelay)))
          .to[List]
          .result)
      _ <- {
        db.run(reportDataTableQuery.insertOrUpdateAll(delaisToAdd.map(e => ReportData(e._1, Some(e._2.toMillis), e._3))))
      }
    } yield Unit
  }

  def updateReportResponseDelay = {
    for {
      delaisToAdd <- db.run(
        eventRepository.eventTableQuery
          .filter(_.action === ActionEvent.REPONSE_PRO_SIGNALEMENT.value)
          .joinLeft(reportDataTableQuery).on(_.reportId === _.reportId)
          .filterNot(_._2.map(_.responseDelay.isDefined).getOrElse(false))
          .join(eventRepository.eventTableQuery).on(_._1.reportId === _.reportId)
          .filter(_._2.action === ActionEvent.ENVOI_SIGNALEMENT.value)
          .map(result => (result._1._1.reportId, result._1._2.flatMap(_.readDelay), result._1._1.creationDate - result._2.creationDate))
          .to[List]
          .result)
      _ <- {
        logger.debug(s"delaisToAdd $delaisToAdd")
        db.run(reportDataTableQuery.insertOrUpdateAll(delaisToAdd.map(e => ReportData(e._1, e._2, Some(e._3.toMillis)))))
      }
    } yield Unit
  }

  def getReportReadMedianDelay = db
    .run(
      reportDataTableQuery
        .filter(_.readDelay.isDefined)
        .map(readData => percentileCont(0.5).within(readData.readDelay.getOrElse(0d)))
        .result.head
    )

  def getReportResponseMedianDelay = db
    .run(
      reportDataTableQuery
        .filter(_.responseDelay.isDefined)
        .map(readData => percentileCont(0.5).within(readData.responseDelay.getOrElse(0d)))
        .result.head
    )
}
