package repositories

import java.time.{LocalDateTime, YearMonth}
import java.util.{Date, UUID}

import javax.inject.{Inject, Singleton}
import models.{PaginatedResult, Report, ReportFile, ReportsPerMonth}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.DateUtils

import scala.concurrent.{ExecutionContext, Future}

case class ReportFilter(
                         departments: Seq[String] = List(),
                         email: Option[String] = None,
                         siret: Option[String] = None,
                         companyName: Option[String] = None,
                         start: Option[LocalDateTime] = None,
                         end: Option[LocalDateTime] = None,
                         category: Option[String] = None,
                         statusPro: Option[String] = None,
                         statusConso: Option[String] = None,
                         details: Option[String] = None
                       )

@Singleton
class ReportRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._
  import models.DetailInputValue._

  class ReportTable(tag: Tag) extends Table[Report](tag, "signalement") {

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
    def statusConso = column[Option[String]]("status_conso")

    type ReportData = (UUID, String, List[String], List[String], String, String, Option[String], Option[String], LocalDateTime, String, String, String, Boolean, Option[String], Option[String])

    def constructReport: ReportData => Report = {
      case (id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret, creationDate, firstName, lastName, email, contactAgreement, statusPro, statusConso) =>
        Report(Some(id), category, subcategories, details.filter(_ != null).map(string2detailInputValue(_)), companyName, companyAddress, companyPostalCode, companySiret,
          Some(creationDate), firstName, lastName, email, contactAgreement, List.empty, statusPro, statusConso)
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
      creationDate, firstName, lastName, email, contactAgreement, files, statusPro, statusConso) =>
        (id.get, category, subcategories, details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"), companyName, companyAddress, companyPostalCode, companySiret,
          creationDate.get, firstName, lastName, email, contactAgreement, statusPro, statusConso)
    }

    def * =
      (id, category, subcategories, details, companyName, companyAddress, companyPostalCode, companySiret,
        creationDate, firstName, lastName, email, contactAgreement, statusPro, statusConso) <> (constructReport, extractReport.lift)
  }

  private class FileTable(tag: Tag) extends Table[ReportFile](tag, "piece_jointe") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("signalement_id")
    def creationDate = column[LocalDateTime]("date_creation")
    def filename = column[String]("nom")
    def report = foreignKey("report_files_fk", reportId, reportTableQuery)(_.id.?)

    type FileData = (UUID, Option[UUID], LocalDateTime, String)

    def constructFile: FileData => ReportFile = {
      case (id, reportId, creationDate, filename) => ReportFile(id, reportId, creationDate, filename)
    }

    def extractFile: PartialFunction[ReportFile, FileData] = {
      case ReportFile(id, reportId, creationDate, filename) => (id, reportId, creationDate, filename)
    }

    def * =
      (id, reportId, creationDate, filename) <> (constructFile, extractFile.lift)
  }
  
  private val reportTableQuery = TableQuery[ReportTable]
  
  private val fileTableQuery = TableQuery[FileTable]

  private val date_part = SimpleFunction.binary[String, LocalDateTime, Int]("date_part")

  private val array_to_string = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")

  implicit class RegexLikeOps(s: Rep[String]) {
    def regexLike(p: Rep[String]): Rep[Boolean] = {
      val expr = SimpleExpression.binary[String,String,Boolean] { (s, p, qb) =>
        qb.expr(s)
        qb.sqlBuilder += " ~* "
        qb.expr(p)
      }
      expr.apply(s,p)
    }
  }

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

  def nbSignalementsBetweenDates(start: String, end: String = DateUtils.formatTime(LocalDateTime.now), departments: Option[List[String]] = None, event: Option[String] = None) = {

    val whereDepartments = departments match {
      case None => ""
      case Some(seq) => " and (" + seq.map(dep => s"code_postal like '$dep%'").mkString(" or ") + ")"
    }

    val whereEvents = event match {
      case Some("À contacter") => " and events.action = 'À contacter'"
      case Some("Envoi du signalement") => " and events.action = 'Envoi du signalement'"
      case Some("Réponse du professionnel au signalement") => " and events.action = 'Réponse du professionnel au signalement' and events.result_action = 'true'"
      case _ => ""
    }

    /*
    db.run(
        sql"""select count(*)
      from signalement
      where date_creation > to_timestamp($start, 'yyyy-mm-dd hh24:mi:ss')
      and date_creation <= to_timestamp($end, 'yyyy-mm-dd hh24:mi:ss')
      #$whereDepartments
      """.as[(Int)].headOption
      )
    */

    db.run(
      sql"""select count(distinct signalement.id)
         from signalement
         left join events on signalement.id = events.report_id
         where 1 = 1
         and date_creation > to_timestamp($start, 'yyyy-mm-dd hh24:mi:ss')
         and date_creation < to_timestamp($end, 'yyyy-mm-dd hh24:mi:ss')
         #$whereDepartments
         #$whereEvents
      """.as[(Int)].headOption
    )


  }

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
          .filterOpt(filter.companyName) {
            case(table, companyName) => table.companyName like s"${companyName}%"
          }
          .filterOpt(filter.start) {
            case(table, start) => table.creationDate >= start
          }
          .filterOpt(filter.end) {
            case(table, end) => table.creationDate < end
          }
          .filterOpt(filter.category) {
            case(table, category) => table.category === category
          }
          .filterOpt(filter.statusPro) {
            case(table, statusPro) => table.statusPro === statusPro
          }
          .filterOpt(filter.statusConso) {
            case(table, statusConso) => table.statusConso === statusConso
          }
          .filterOpt(filter.details) {
            case(table, details) => array_to_string(table.subcategories, ",", "") ++ array_to_string(table.details, ",", "") regexLike s"${details}"
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
        count <- query.length.result
      } yield PaginatedResult(
        totalCount = count,
        entities = reports,
        hasNextPage = count - ( offset + limit ) > 0
      )
  }

  def createFile(file: ReportFile): Future[ReportFile] = db
    .run(fileTableQuery += file)
    .map(_ => file)

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID) = {
    val queryFile = for (refFile <- fileTableQuery.filter(_.id.inSet(fileIds)))
      yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  def getFile(uuid: UUID): Future[Option[ReportFile]] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .to[List].result
        .headOption
    )

  def retrieveReportFiles(reportId: UUID): Future[List[ReportFile]] = db
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

