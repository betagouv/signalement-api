package repositories

import java.time._
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.ReportData
import play.api.{Configuration, Logger}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.Constants.ActionEvent
import com.github.tminglei.slickpg.agg.PgAggFuncSupport.OrderedSetAggFunctions._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportDataRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     val eventRepository: EventRepository,
                                     val reportRepository: ReportRepository,
                                     configuration: Configuration)(implicit ec: ExecutionContext) {

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

  val backofficeProStartDate = OffsetDateTime.of(
    LocalDate.parse(configuration.get[String]("play.stats.backofficeProStartDate")),
    LocalTime.MIDNIGHT,
    ZoneOffset.UTC)

  def updateReportReadDelay = {
    for {
      delaisToAdd <- db.run(
        eventRepository.eventTableQuery
          .filter(_.action === ActionEvent.ENVOI_SIGNALEMENT.value)
          .filter(_.creationDate > backofficeProStartDate)
          .joinLeft(reportDataTableQuery).on(_.reportId === _.reportId)
          .filterNot(_._2.flatMap(_.readDelay).isDefined)
          .join(reportRepository.reportTableQuery).on(_._1.reportId === _.id)
          .map(result => (result._1._1.reportId, result._1._1.creationDate - result._2.creationDate, result._1._2.flatMap(_.responseDelay)))
          .to[List]
          .result)
      _ <- {
        db.run(reportDataTableQuery.insertOrUpdateAll(delaisToAdd.map(e => ReportData(e._1.get, Some(e._2.toMinutes), e._3))))
      }
    } yield Unit
  }

  def updateReportResponseDelay = {
    for {
      delaisToAdd <- db.run(
        eventRepository.eventTableQuery
          .filter(_.action === ActionEvent.REPONSE_PRO_SIGNALEMENT.value)
          .filter(_.creationDate > backofficeProStartDate)
          .joinLeft(reportDataTableQuery).on(_.reportId === _.reportId)
          .filterNot(_._2.flatMap(_.responseDelay).isDefined)
          .join(eventRepository.eventTableQuery).on(_._1.reportId === _.reportId)
          .filter(_._2.action === ActionEvent.ENVOI_SIGNALEMENT.value)
          .map(result => (result._1._1.reportId, result._1._2.flatMap(_.readDelay), result._1._1.creationDate - result._2.creationDate))
          .to[List]
          .result)
      _ <- {
        db.run(reportDataTableQuery.insertOrUpdateAll(delaisToAdd.map(e => ReportData(e._1.get, e._2, Some(e._3.toMinutes)))))
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
