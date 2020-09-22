package repositories

import java.time._
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.Configuration
import repositories._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.{GetResult, JdbcProfile}
import utils.Constants.ActionEvent.REPORT_COMPANY_CHANGE
import utils.Constants.{Departments, ReportStatus}
import utils.Constants.ReportStatus.ReportStatusValue
import utils.{Address, DateUtils, EmailAddress, SIRET, URL}

import scala.concurrent.{ExecutionContext, Future}

case class ReportFilter(
                         departments: Seq[String] = List(),
                         email: Option[String] = None,
                         siret: Option[String] = None,
                         companyName: Option[String] = None,
                         start: Option[LocalDate] = None,
                         end: Option[LocalDate] = None,
                         category: Option[String] = None,
                         statusList: Seq[ReportStatusValue] = List(),
                         details: Option[String] = None,
                         employeeConsumer: Option[Boolean] = None,
                         hasCompany: Option[Boolean] = None,
                         tags: Seq[String] = Nil
                       )

@Singleton
class ReportRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 accessTokenRepository: AccessTokenRepository,
                                 val companyRepository: CompanyRepository,
                                 configuration: Configuration)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val zoneId = ZoneId.of(configuration.get[String]("play.zoneId"))

  import PostgresProfile.api._
  import dbConfig._
  import models.DetailInputValue._

  class ReportTable(tag: Tag) extends Table[Report](tag, "reports") {

    def id = column[UUID]("id", O.PrimaryKey)
    def category = column[String]("category")
    def subcategories = column[List[String]]("subcategories")
    def details = column[List[String]]("details")
    def companyId = column[Option[UUID]]("company_id")
    def companyName = column[Option[String]]("company_name")
    def companyAddress = column[Option[Address]]("company_address")
    def companyPostalCode = column[Option[String]]("company_postal_code")
    def companySiret = column[Option[SIRET]]("company_siret")
    def websiteId = column[Option[UUID]]("website_id")
    def websiteURL = column[Option[URL]]("website_url")
    def creationDate= column[OffsetDateTime]("creation_date")
    def firstName = column[String]("first_name")
    def lastName = column[String]("last_name")
    def email = column[EmailAddress]("email")
    def contactAgreement = column[Boolean]("contact_agreement")
    def employeeConsumer = column[Boolean]("employee_consumer")
    def status = column[String]("status")
    def contractualDispute = column[Boolean]("contractual_dispute")
    def tags = column[List[String]]("tags")

    def company = foreignKey("COMPANY_FK", companyId, companyRepository.companyTableQuery)(_.id.?, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Cascade)

    type ReportData = (UUID, String, List[String], List[String], Option[UUID], Option[String], Option[Address], Option[String], Option[SIRET], Option[UUID], Option[URL],
      OffsetDateTime, String, String, EmailAddress, Boolean, Boolean, String, Boolean, List[String])

    def constructReport: ReportData => Report = {
      case (id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
      websiteId, websiteURL, creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, status, contractualDispute, tags) =>
        Report(id, category, subcategories, details.filter(_ != null).map(string2detailInputValue(_)), companyId, companyName, companyAddress, companyPostalCode, companySiret, websiteId,
          websiteURL, creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, ReportStatus.fromDefaultValue(status), contractualDispute, tags)
    }

    def extractReport: PartialFunction[Report, ReportData] = {
      case Report(id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
      websiteId, websiteURL, creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, status, contractualDispute, tags) =>
        (id, category, subcategories, details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"), companyId, companyName, companyAddress, companyPostalCode, companySiret,
          websiteId, websiteURL, creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, status.defaultValue, contractualDispute, tags)
    }

    def * =
      (id, category, subcategories, details, companyId, companyName, companyAddress, companyPostalCode, companySiret,
        websiteId, websiteURL, creationDate, firstName, lastName, email, contactAgreement, employeeConsumer, status, contractualDispute, tags) <> (constructReport, extractReport.lift)
  }

  implicit val ReportFileOriginColumnType = MappedColumnType.base[ReportFileOrigin, String](_.value, ReportFileOrigin(_))

  private class FileTable(tag: Tag) extends Table[ReportFile](tag, "report_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("report_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[String]("filename")
    def storageFilename = column[String]("storage_filename")
    def origin = column[ReportFileOrigin]("origin")
    def report = foreignKey("report_files_fk", reportId, reportTableQuery)(_.id.?)

    type FileData = (UUID, Option[UUID], OffsetDateTime, String, String, ReportFileOrigin)

    def constructFile: FileData => ReportFile = {
      case (id, reportId, creationDate, filename, storageFilename, origin) => ReportFile(id, reportId, creationDate, filename, storageFilename, origin)
    }

    def extractFile: PartialFunction[ReportFile, FileData] = {
      case ReportFile(id, reportId, creationDate, filename, storageFilename, origin) => (id, reportId, creationDate, filename, storageFilename, origin)
    }

    def * =
      (id, reportId, creationDate, filename, storageFilename, origin) <> (constructFile, extractFile.lift)
  }

  val reportTableQuery = TableQuery[ReportTable]

  private val fileTableQuery = TableQuery[FileTable]

  private val companyTableQuery = companyRepository.companyTableQuery

  private val date = SimpleFunction.unary[OffsetDateTime, LocalDate]("date")

  private val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

  private val array_to_string = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")

  val backofficeAdminStartDate = OffsetDateTime.of(
    LocalDate.parse(configuration.get[String]("play.stats.backofficeAdminStartDate")),
    LocalTime.MIDNIGHT,
    ZoneOffset.UTC)

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

  def list: Future[List[Report]] = db.run(reportTableQuery.to[List].result)

  def update(report: Report): Future[Report] = {
    val queryReport = for (refReport <- reportTableQuery if refReport.id === report.id)
      yield refReport
    db.run(queryReport.update(report))
      .map(_ => report)
  }

  def count(siret: Option[SIRET] = None): Future[Int] = db
    .run(reportTableQuery
      .filterOpt(siret) {
        case(table, siret) => table.companySiret === siret
      }
      .length.result)

  def monthlyCount: Future[List[MonthlyStat]] = db
    .run(
      reportTableQuery
        .filter(report => report.creationDate > OffsetDateTime.now().minusMonths(11).withDayOfMonth(1))
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => MonthlyStat(result._3, YearMonth.of(result._2, result._1))))

  val baseStatReportTableQuery = reportTableQuery
    .filter(_.creationDate > backofficeAdminStartDate)
  val baseMonthlyStatReportTableQuery = baseStatReportTableQuery.filter(report => report.creationDate > OffsetDateTime.now().minusMonths(11).withDayOfMonth(1))

  def countWithStatus(statusList: List[ReportStatusValue], cutoff: Option[Duration]) = db
    .run(
      baseStatReportTableQuery
        .filterIf(cutoff.isDefined)(_.creationDate < OffsetDateTime.now().minus(cutoff.get))
        .filter(_.status inSet statusList.map(_.defaultValue))
        .length
        .result
    )

  def countMonthlyWithStatus(statusList: List[ReportStatusValue]): Future[List[MonthlyStat]] = db
    .run(
      baseMonthlyStatReportTableQuery
        .filter(_.status inSet statusList.map(_.defaultValue))
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map{
          case ((month, year), group) => (month, year, group.length)
        }
        .to[List].result
    )
    .map(_.map(result => MonthlyStat(result._3, YearMonth.of(result._2, result._1))))


  def getReport(id: UUID): Future[Option[Report]] = db.run {
    reportTableQuery
      .filter(_.id === id)
      .result
      .headOption
  }

  def delete(id: UUID): Future[Int] = db.run {
    reportTableQuery
      .filter(_.id === id)
      .delete
  }

  def getReports(companyId: UUID) = db.run {
    reportTableQuery
      .filter(_.companyId === companyId)
      .to[List]
      .result
  }

  def getReports(offset: Long, limit: Int, filter: ReportFilter): Future[PaginatedResult[Report]] = db.run {
    val query = reportTableQuery
        .filterOpt(filter.email) {
          case(table, email) => table.email === EmailAddress(email)
        }
        .filterOpt(filter.siret) {
          case(table, siret) => table.companySiret === SIRET(siret)
        }
        .filterOpt(filter.companyName) {
          case(table, companyName) => table.companyName like s"${companyName}%"
        }
        .filterOpt(filter.start) {
          case(table, start) => table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, zoneId).toOffsetDateTime
        }
        .filterOpt(filter.end) {
          case(table, end) => table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, zoneId).toOffsetDateTime
        }
        .filterOpt(filter.category) {
          case(table, category) => table.category === category
        }
        .filterOpt(filter.hasCompany) {
          case (table, hasCompany) => table.companyId.isDefined === hasCompany
        }
        .filterIf(filter.statusList.length > 0 && filter.statusList != ReportStatus.reportStatusList) {
          case table => table.status.inSet(filter.statusList.map(_.defaultValue))
        }
        .filterIf(!filter.tags.isEmpty) {
          case table => table.tags @& filter.tags.toList.bind
        }
        .filterOpt(filter.details) {
          case(table, details) => array_to_string(table.subcategories, ",", "") ++ array_to_string(table.details, ",", "") regexLike s"${details}"
        }
        .filterOpt(filter.employeeConsumer) {
          case(table, employeeConsumer) => table.employeeConsumer === employeeConsumer
        }
      .joinLeft(companyTableQuery).on(_.companyId === _.id)
      .filterIf(filter.departments.length > 0) {
        case (report, company) => company.map(_.department).flatten.map(a => a.inSet(filter.departments)).getOrElse(false)
      }

    for {
        reports <- query
          .map(_._1)
          .sortBy(_.creationDate.desc)
          .drop(offset)
          .take(limit)
          .to[List]
          .result
        count <- query.length.result
      } yield PaginatedResult(
        totalCount = count,
        entities = reports,
        hasNextPage = count - ( offset + limit ) > 0
      )
  }

  def getReportsByIds(ids: List[UUID]): Future[List[Report]] = db.run(
    reportTableQuery.filter(_.id inSet(ids))
      .to[List]
      .result
  )

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

  def prefetchReportsFiles(reportsIds: List[UUID]): Future[Map[UUID, List[ReportFile]]] = {
    db.run(fileTableQuery.filter(
      _.reportId inSetBind reportsIds
    ).to[List].result)
      .map(events =>
        events.groupBy(_.reportId.get)
      )
  }

  def deleteFile(uuid: UUID): Future[Int] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .delete
    )

  def getNbReportsGroupByCompany(offset: Long, limit: Int): Future[PaginatedResult[CompanyWithNbReports]] = {
    val q = db.run(companyTableQuery.joinLeft(reportTableQuery).on(_.id === _.companyId)
        .groupBy(_._1)
        .map{ case (grouped, all) => (grouped, all.map(_._2).size) }
        .to[List]
        .sortBy(_._2.desc)
        .result)

    for {
      res <- q.map(_.map{case (company, cnt) => CompanyWithNbReports(company, cnt)})
    } yield {
      PaginatedResult(
        totalCount = res.length,
        entities = res.drop(offset.toInt).take(limit).toList,
        hasNextPage = res.length - ( offset + limit ) > 0
      )
    }
  }

  def getReportsForStatusWithAdmins(status: ReportStatusValue): Future[List[(Report, List[User])]] = {
    for {
      reports <- db.run {
                  reportTableQuery
                    .filter(_.status === status.defaultValue)
                    .to[List]
                    .result
                }
      adminsMap <- companyRepository.fetchAdminsByCompany(reports.flatMap(_.companyId))
    } yield reports.flatMap(r => r.companyId.map(companyId => (r, adminsMap.getOrElse(companyId, Nil))))
  }

  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]] = db
    .run(
      reportTableQuery
        .filter(_.status === ReportStatus.TRAITEMENT_EN_COURS.defaultValue)
        .filter(_.companyId inSet companiesIds)
        .to[List].result
    )
}
