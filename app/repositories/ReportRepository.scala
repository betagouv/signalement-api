package repositories

import java.time.{LocalDateTime, YearMonth}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{Event, File, Report, ReportsPerMonth}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, OFormat}
import utils.Constants
import utils.Constants.EventType.EventTypeValue

case class PaginatedResult[T](
  totalCount: Int, 
  hasNextPage: Boolean,
  entities: List[T]
)

object PaginatedResult {
  implicit val paginatedResultFormat: OFormat[PaginatedResult[Report]] = Json.format[PaginatedResult[Report]]
}

case class ReportFilter(departments: Seq[String] = List(), email: Option[String] = None, siret: Option[String] = None, entreprise: Option[String] = None)

case class EventFilter(eventType: Option[EventTypeValue])

@Singleton
class ReportRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._
  import models.DetailInputValue._

  private class ReportTable(tag: Tag) extends Table[Report](tag, "signalement") {

    def id = column[UUID]("id", O.PrimaryKey)
    def category = column[String]("categorie")
    def subcategories = column[List[String]]("sous_categories")
    def details = column[List[String]]("details")
    def companyName = column[String]("nom_etablissement")
    def companyAddress = column[String]("adresse_etablissement")
    def companyPostalCode = column[Option[String]]("code_postal")
    def companySiret = column[Option[String]]("siret_etablissement")
    def creationDate= column[LocalDateTime]("date_creation")
    def firstName = column[String]("prenom")
    def lastName = column[String]("nom")
    def email = column[String]("email")
    def contactAgreement = column[Boolean]("accord_contact")
    def statusPro = column[Option[String]]("status_pro")

    type ReportData = (UUID, String, List[String], List[String], String, String, Option[String], Option[String], LocalDateTime, String, String, String, Boolean, Option[String])

    def constructReport: ReportData => Report = {
      case (id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret, creationDate, firstName, lastName, email, contactAgreement, statusPro) =>
        Report(Some(id), category, subcategories, details.filter(_ != null).map(string2detailInputValue(_)), companyName, companyAddress, companyPostalCode, companySiret,
          Some(creationDate), firstName, lastName, email, contactAgreement, List.empty, statusPro)
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, firstName, lastName, email, contactAgreement, files, statusPro) =>
        (id.get, category, subcategories, details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"), companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.get, firstName, lastName, email, contactAgreement, statusPro)
    }

    def * =
      (id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, firstName, lastName, email, contactAgreement, statusPro) <> (constructReport, extractReport.lift)
  }

  private class FileTable(tag: Tag) extends Table[File](tag, "piece_jointe") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("signalement_id")
    def creationDate = column[LocalDateTime]("date_creation")
    def filename = column[String]("nom")
    def report = foreignKey("report_files_fk", reportId, reportTableQuery)(_.id.?)

    type FileData = (UUID, Option[UUID], LocalDateTime, String)

    def constructFile: FileData => File = {
      case (id, reportId, creationDate, filename) => File(id, reportId, creationDate, filename)
    }

    def extractFile: PartialFunction[File, FileData] = {
      case File(id, reportId, creationDate, filename) => (id, reportId, creationDate, filename)
    }

    def * =
      (id, reportId, creationDate, filename) <> (constructFile, extractFile.lift)
  }

  private class EventTable(tag: Tag) extends Table[Event](tag, "events") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[UUID]("report_id")
    def userId = column[UUID]("user_id")
    def creationDate = column[LocalDateTime]("creation_date")
    def eventType = column[String]("event_type")
    def action = column[String]("action")
    def resultAction = column[Option[String]]("result_action")
    def detail = column[Option[String]]("detail")
    def report = foreignKey("fk_events_report", reportId, reportTableQuery)(_.id)
    //def user = foreignKey("fk_events_users", userId, userTableQuery)(_.id.?)

    type EventData = (UUID, UUID, UUID, LocalDateTime, String, String, Option[String], Option[String])

    def constructEvent: EventData => Event = {

      case (id, reportId, userId, creationDate, eventType, action, resultAction, detail) => {
        Event(Some(id), Some(reportId), userId, Some(creationDate), Constants.EventType.fromValue(eventType).get,
          Constants.ActionEvent.fromValue(action).get, resultAction, detail)
      }
    }

    def extractEvent: PartialFunction[Event, EventData] = {
      case Event(id, reportId, userId, creationDate, eventType, action, resultAction, detail) =>
        (id.get, reportId.get, userId, creationDate.get, eventType.value, action.value, resultAction, detail)
    }

    def * =
      (id, reportId, userId, creationDate, eventType, action, resultAction, detail) <> (constructEvent, extractEvent.lift)
  }
  
  private val reportTableQuery = TableQuery[ReportTable]
  
  private val fileTableQuery = TableQuery[FileTable]
  
  private val eventTableQuery = TableQuery[EventTable]

  private val date_part = SimpleFunction.binary[String, LocalDateTime, Int]("date_part")

  def create(report: Report): Future[Report] = db
    .run(reportTableQuery += report)
    .map(_ => report)


  def update(report: Report): Future[Report] = {
    val queryReport = for (refReport <- reportTableQuery if refReport.id === report.id)
      yield refReport
    db.run(queryReport.update(report))
      .map(_ => report)
  }

  def count: Future[Int] = db
    .run(reportTableQuery.length.result)

  def countPerMonth: Future[List[ReportsPerMonth]] = db
    .run(
      reportTableQuery
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => ReportsPerMonth(result._3, YearMonth.of(result._2, result._1))))

  def getReport(id: UUID): Future[Option[Report]] = db.run {
    reportTableQuery
      .filter(_.id === id)
      .joinLeft(fileTableQuery).on(_.id === _.reportId)
      .to[List]
      .result
      .map(result =>
        result.map(_._1).distinct
          .map(report => report.copy(files = result.map(_._2).distinct.filter(_.map(_.reportId == report.id).getOrElse(false)).map(_.get)))
          .headOption
      )
  }

  def delete(id: UUID): Future[Int] = db.run {
    reportTableQuery
      .filter(_.id === id)
      .delete
  }

  def getReports(offset: Long, limit: Int, filter: ReportFilter): Future[PaginatedResult[Report]] = db.run {

      val query = reportTableQuery
          .filterIf(filter.departments.length > 0) {
            case table => table.companyPostalCode.map(cp => cp.substring(0, 2).inSet(filter.departments)).getOrElse(false)
          }
          .filterOpt(filter.email) {
            case(table, email) => table.email === email
          }
          .filterOpt(filter.siret) {
            case(table, siret) => table.companySiret === siret
          }
          .filterOpt(filter.entreprise) {
            case(table, entreprise) => table.companyName like s"${entreprise}%"
          }
    
      for {
        reports <- query
          .sortBy(_.creationDate.desc)
          .drop(offset)
          .take(limit)
          .joinLeft(fileTableQuery).on(_.id === _.reportId)
          .sortBy(_._1.creationDate.desc)
          .to[List]
          .result
          .map(result =>
            result.map(_._1).distinct
              .map(report => report.copy(files = result.map(_._2).distinct.filter(_.map(_.reportId == report.id).getOrElse(false)).map(_.get)))
          )
          //.map(result =>
          //  result.map(_._1)
          //    .map(report => report.copy(files = result.flatMap(_._2).filter(_.reportId == report.id)))
          //)
        count <- query.length.result
      } yield PaginatedResult(
        totalCount = count,
        entities = reports,
        hasNextPage = count - ( offset + limit ) > 0
      )
  }

  def createEvent(event: Event): Future[Event] = db
    .run(eventTableQuery += event)
    .map(_ => event)

  def getEvents(uuidReport: UUID, filter: EventFilter): Future[List[Event]] = db.run {
    eventTableQuery
      .filter(_.reportId === uuidReport)
      .filterOpt(filter.eventType) {
        case (table, eventType) => table.eventType === eventType.value
      }
      .sortBy(_.creationDate.desc)
      .to[List]
      .result
  }

  def createFile(file: File): Future[File] = db
    .run(fileTableQuery += file)
    .map(_ => file)

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID) = {
    val queryFile = for (refFile <- fileTableQuery.filter(_.id.inSet(fileIds)))
      yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  def getFile(uuid: UUID): Future[Option[File]] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .to[List].result
        .headOption
    )

  def retrieveReportFiles(reportId: UUID): Future[List[File]] = db
    .run(
      fileTableQuery
        .filter(_.reportId === reportId)
        .to[List].result
    )

  def deleteFile(uuid: UUID): Future[Int] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .delete
    )

}

