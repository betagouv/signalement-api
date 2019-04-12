package repositories

import java.sql.Date
import java.time.{LocalDateTime, YearMonth}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{Report, ReportsPerMonth}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json.{Json, OFormat}


case class PaginatedResult[T](
  totalCount: Int, 
  hasNextPage: Boolean,
  entities: List[T]
)

object PaginatedResult {
  implicit val paginatedResultFormat: OFormat[PaginatedResult[Report]] = Json.format[PaginatedResult[Report]]
}

case class ReportFilter(codePostal: Option[String])

@Singleton
class ReportRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  private class ReportTable(tag: Tag) extends Table[Report](tag, "signalement") {

    def id = column[UUID]("id", O.PrimaryKey)
    def companyType = column[Option[String]]("type_etablissement")
    def category = column[String]("categorie")
    def subcategory = column[Option[String]]("sous_categorie")
    def precision = column[Option[String]]("precision")
    def companyName = column[String]("nom_etablissement")
    def companyAddress = column[String]("adresse_etablissement")
    def companyPostalCode = column[Option[String]]("code_postal")
    def companySiret = column[Option[String]]("siret_etablissement")
    def creationDate= column[LocalDateTime]("date_creation")
    def anomalyDate= column[Date]("date_constat")
    def anomalyTimeSlot = column[Option[Int]]("heure_constat")
    def description = column[Option[String]]("description")
    def firstName = column[String]("prenom")
    def lastName = column[String]("nom")
    def email = column[String]("email")
    def contactAgreement = column[Boolean]("accord_contact")
    def fileIds = column[List[UUID]]("piece_jointe_ids")

    type ReportData = (UUID, Option[String], String, Option[String], Option[String], String, String, Option[String], Option[String], LocalDateTime, Date, Option[Int], Option[String], String, String, String, Boolean, List[UUID])

    def constructReport: ReportData => Report = {
      case (id, companyType, category, subcategory, precision, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, anomalyDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, fileIds) =>
        Report(Some(id), category, subcategory, precision, companyName, companyAddress, companyPostalCode, companySiret,
          Some(creationDate), anomalyDate.toLocalDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, fileIds)
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategory, precision, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, anomalyDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, fileIds) =>
        (id.get, None, category, subcategory, precision, companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.get, Date.valueOf(anomalyDate), anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, fileIds)
    }

    def * =
      (id, companyType, category, subcategory, precision, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, anomalyDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, fileIds) <> (constructReport, extractReport.lift)
  }

  private val reportTableQuery = TableQuery[ReportTable]

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

  def getReportsSimple(offset: Int, limit: Int): Future[List[Report]] = db
    .run(
        reportTableQuery
        .drop(offset)
        .take(limit)
        .to[List]
        .result
    )

  def getReports(offset: Long, limit: Int, filter: ReportFilter): Future[PaginatedResult[Report]] = db.run {
      for {
        reports <- reportTableQuery
          .filterOpt(filter.codePostal) {
            case(table, codePostal) => table.companyPostalCode === codePostal
          }
          .sortBy(_.creationDate.desc)
          .drop(offset)
          .take(limit)
          .result
      } yield PaginatedResult(
        totalCount = reports.toList.length,
        entities = reports.toList,
        hasNextPage = reports.toList.length - ( offset + limit ) >0
      )
    }

    def getFieldsNames: Seq[String] = {
      reportTableQuery.baseTableRow.create_*.map(_.name).toSeq
    }

    def getFieldsClassName = {

      List(
        "id",
        "companyType",
        "category",
        "subcategory",
        "precision",
        "companyName",
        "companyAddress",
        "companyPostalCode",
        "companySiret",
        "creationDate",
        "anomalyDate",
        "anomalyTimeSlot",
        "description",
        "firstName",
        "lastName",
        "email",
        "contactAgreement",
        "fileIds"
      )

    }

}

