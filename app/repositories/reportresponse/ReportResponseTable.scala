package repositories.reportresponse

import models.report.ExistingResponseDetails
import models.report.ReportResponseType
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.util.UUID

class ReportResponseTable(tag: Tag) extends DatabaseTable[ReportResponse](tag, "report_responses") {

  def eventId         = column[UUID]("event_id")
  def reportId        = column[Option[UUID]]("report_id")
  def userId          = column[Option[UUID]]("user_id")
  def companyId       = column[Option[UUID]]("company_id")
  def creationDate    = column[OffsetDateTime]("creation_date")
  def responseType    = column[String]("response_type")
  def responseDetails = column[Option[String]]("response_details")
  def consumerDetails = column[Option[String]]("consumer_details")
  def dgccrfDetails   = column[Option[String]]("dgccrf_details")

  type ReportResponseData = (
      UUID,
      Option[UUID],
      Option[UUID],
      Option[UUID],
      OffsetDateTime,
      String,
      Option[String],
      Option[String],
      Option[String]
  )

  def constructReportResponse: ReportResponseData => ReportResponse = {
    case (
          eventId,
          reportId,
          userId,
          companyId,
          creationDate,
          responseType,
          responseDetails,
          consumerDetails,
          dgccrfDetails
        ) =>
      ReportResponse(
        eventId,
        reportId,
        userId,
        companyId,
        creationDate,
        ReportResponseType.withName(responseType),
        responseDetails.map(ExistingResponseDetails.withName),
        consumerDetails,
        dgccrfDetails
      )
  }

  def extractReportResponse: PartialFunction[ReportResponse, ReportResponseData] = {
    case ReportResponse(
          eventId,
          reportId,
          userId,
          companyId,
          creationDate,
          responseType,
          responseDetails,
          consumerDetails,
          dgccrfDetails
        ) =>
      (
        eventId,
        reportId,
        userId,
        companyId,
        creationDate,
        responseType.entryName,
        responseDetails.map(_.entryName),
        consumerDetails,
        dgccrfDetails
      )
  }

  def * =
    (
      eventId,
      reportId,
      userId,
      companyId,
      creationDate,
      responseType,
      responseDetails,
      consumerDetails,
      dgccrfDetails
    ) <> (constructReportResponse, extractReportResponse.lift)
}

object ReportResponseTable {
  val table = TableQuery[ReportResponseTable]
}
