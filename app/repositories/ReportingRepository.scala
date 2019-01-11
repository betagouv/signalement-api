package repositories

import java.sql.Date
import java.time.YearMonth
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.{Reporting, ReportsPerMonth}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportingRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  private class ReportingTable(tag: Tag) extends Table[Reporting](tag, "signalement") {

    def id = column[UUID]("id", O.PrimaryKey)
    def companyType = column[String]("type_etablissement")
    def anomalyCategory = column[String]("categorie_anomalie")
    def anomalyPrecision = column[Option[String]]("precision_anomalie")
    def companyName = column[String]("nom_etablissement")
    def companyAddress = column[String]("adresse_etablissement")
    def companyPostalCode = column[Option[String]]("code_postal")
    def companySiret = column[Option[String]]("siren_etablissement")
    def creationDate= column[Date]("date_creation")
    def anomalyDate= column[Date]("date_constat")
    def anomalyTimeSlot = column[Option[Int]]("heure_constat")
    def description = column[Option[String]]("description")
    def firstName = column[String]("prenom")
    def lastName = column[String]("nom")
    def email = column[String]("email")
    def contactAgreement = column[Boolean]("accord_contact")
    def ticketFileId = column[Option[Long]]("ticket_file_id")
    def anomalyFileId = column[Option[Long]]("anomalie_file_id")

    type ReportingData = (UUID, String, String, Option[String], String, String, Option[String], Option[String], Date, Date, Option[Int], Option[String], String, String, String, Boolean, Option[Long], Option[Long])

    def constructReporting: ReportingData => Reporting = {
      case (id, companyType, anomalyCategory, anomalyPrecision, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, anomalyDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, ticketFileId, anomalyFileId) =>
        Reporting(id, companyType, anomalyCategory, anomalyPrecision, companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.toLocalDate, anomalyDate.toLocalDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, ticketFileId, anomalyFileId)
    }

    def extractReporting: PartialFunction[Reporting, ReportingData] = {
      case Reporting(id, companyType, anomalyCategory, anomalyPrecision, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, anomalyDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, ticketFileId, anomalyFileId) =>
        (id, companyType, anomalyCategory, anomalyPrecision, companyName, companyAddress, companyPostalCode, companySiret,
          Date.valueOf(creationDate), Date.valueOf(anomalyDate), anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, ticketFileId, anomalyFileId)
    }

    def * =
      (id, companyType, anomalyCategory, anomalyPrecision, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, anomalyDate, anomalyTimeSlot, description, firstName, lastName, email, contactAgreement, ticketFileId, anomalyFileId) <> (constructReporting, extractReporting.lift)
  }

  private val reportingTableQuery = TableQuery[ReportingTable]

  private val date_part = SimpleFunction.binary[String, Date, Int]("date_part")

  def create(reporting: Reporting): Future[Reporting] = db
    .run(reportingTableQuery += reporting)
    .map(_ => reporting)


  def update(reporting: Reporting): Future[Reporting] = {
    val queryReporting = for (refReporting <- reportingTableQuery if refReporting.id === reporting.id)
      yield refReporting
    db.run(queryReporting.update(reporting))
      .map(_ => reporting)
  }

  def count: Future[Int] = db
    .run(reportingTableQuery.length.result)

  def countPerMonth: Future[List[ReportsPerMonth]] = db
    .run(
      reportingTableQuery
        .groupBy(reporting => (date_part("month", reporting.creationDate), date_part("year", reporting.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => ReportsPerMonth(result._3, YearMonth.of(result._2, result._1))))
}

