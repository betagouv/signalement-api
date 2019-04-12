package repositories

import java.time.{LocalDateTime, YearMonth}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{DetailInputValue, Report, ReportsPerMonth}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

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
    def fileIds = column[List[UUID]]("piece_jointe_ids")

    type ReportData = (UUID, String, List[String], List[String], String, String, Option[String], Option[String], LocalDateTime, String, String, String, Boolean, List[UUID])

    def constructReport: ReportData => Report = {
      case (id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, firstName, lastName, email, contactAgreement, fileIds) =>
        Report(Some(id), category, subcategories, details.map(string2detailInputValue(_)), companyName, companyAddress, companyPostalCode, companySiret,
          Some(creationDate), firstName, lastName, email, contactAgreement, fileIds)
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, firstName, lastName, email, contactAgreement, fileIds) =>
        (id.get, category, subcategories, details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"), companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.get, firstName, lastName, email, contactAgreement, fileIds)
    }

    def * =
      (id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, firstName, lastName, email, contactAgreement, fileIds) <> (constructReport, extractReport.lift)
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
}

