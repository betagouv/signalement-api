package repositories

import models.DetailInputValue.toDetailInputValue
import models._
import play.api.Configuration
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import utils.Constants.ReportStatus
import utils.Constants.ReportStatus.ReportStatusValue
import utils._

import java.time.OffsetDateTime
import java.time._
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportTable(tag: Tag) extends Table[Report](tag, "reports") {

  def id = column[UUID]("id", O.PrimaryKey)
  def category = column[String]("category")
  def subcategories = column[List[String]]("subcategories")
  def details = column[List[String]]("details")
  def companyId = column[Option[UUID]]("company_id")
  def companyName = column[Option[String]]("company_name")
  def companySiret = column[Option[SIRET]]("company_siret")
  def companyStreetNumber = column[Option[String]]("company_street_number")
  def companyStreet = column[Option[String]]("company_street")
  def companyAddressSupplement = column[Option[String]]("company_address_supplement")
  def companyPostalCode = column[Option[String]]("company_postal_code")
  def companyCity = column[Option[String]]("company_city")
  def companyCountry = column[Option[Country]]("company_country")
  def websiteURL = column[Option[URL]]("website_url")
  def host = column[Option[String]]("host")
  def phone = column[Option[String]]("phone")
  def creationDate = column[OffsetDateTime]("creation_date")
  def firstName = column[String]("first_name")
  def lastName = column[String]("last_name")
  def email = column[EmailAddress]("email")
  def contactAgreement = column[Boolean]("contact_agreement")
  def employeeConsumer = column[Boolean]("employee_consumer")
  def forwardToReponseConso = column[Boolean]("forward_to_reponseconso")
  def status = column[String]("status")
  def vendor = column[Option[String]]("vendor")
  def tags = column[List[String]]("tags")

  def company = foreignKey("COMPANY_FK", companyId, CompanyTables.tables)(
    _.id.?,
    onUpdate = ForeignKeyAction.Restrict,
    onDelete = ForeignKeyAction.Cascade
  )

  type ReportData = (
      UUID,
      String,
      List[String],
      List[String],
      (
          Option[UUID],
          Option[String],
          Option[SIRET],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[Country]
      ),
      (Option[URL], Option[String]),
      Option[String],
      OffsetDateTime,
      String,
      String,
      EmailAddress,
      Boolean,
      Boolean,
      Boolean,
      String,
      Option[String],
      List[String]
  )

  def constructReport: ReportData => Report = {
    case (
          id,
          category,
          subcategories,
          details,
          (
            companyId,
            companyName,
            companySiret,
            companyStreetNumber,
            companyStreet,
            companyAddressSupplement,
            companyPostalCode,
            companyCity,
            companyCountry
          ),
          (websiteURL, host),
          phone,
          creationDate,
          firstName,
          lastName,
          email,
          contactAgreement,
          employeeConsumer,
          forwardToReponseConso,
          status,
          vendor,
          tags
        ) =>
      Report(
        id = id,
        category = category,
        subcategories = subcategories,
        details = details.filter(_ != null).map(toDetailInputValue),
        companyId = companyId,
        companyName = companyName,
        companyAddress = Address(
          number = companyStreetNumber,
          street = companyStreet,
          addressSupplement = companyAddressSupplement,
          postalCode = companyPostalCode,
          city = companyCity,
          country = companyCountry
        ),
        companySiret = companySiret,
        websiteURL = WebsiteURL(websiteURL, host),
        phone = phone,
        creationDate = creationDate,
        firstName = firstName,
        lastName = lastName,
        email = email,
        contactAgreement = contactAgreement,
        employeeConsumer = employeeConsumer,
        forwardToReponseConso = forwardToReponseConso,
        status = ReportStatus.fromDefaultValue(status),
        vendor = vendor,
        tags = tags
      )
  }

  def extractReport: PartialFunction[Report, ReportData] = { case r =>
    (
      r.id,
      r.category,
      r.subcategories,
      r.details.map(detailInputValue => s"${detailInputValue.label} ${detailInputValue.value}"),
      (
        r.companyId,
        r.companyName,
        r.companySiret,
        r.companyAddress.number,
        r.companyAddress.street,
        r.companyAddress.addressSupplement,
        r.companyAddress.postalCode,
        r.companyAddress.city,
        r.companyAddress.country
      ),
      (r.websiteURL.websiteURL, r.websiteURL.host),
      r.phone,
      r.creationDate,
      r.firstName,
      r.lastName,
      r.email,
      r.contactAgreement,
      r.employeeConsumer,
      r.forwardToReponseConso,
      r.status.defaultValue,
      r.vendor,
      r.tags
    )
  }

  def * = (
    id,
    category,
    subcategories,
    details,
    (
      companyId,
      companyName,
      companySiret,
      companyStreetNumber,
      companyStreet,
      companyAddressSupplement,
      companyPostalCode,
      companyCity,
      companyCountry
    ),
    (websiteURL, host),
    phone,
    creationDate,
    firstName,
    lastName,
    email,
    contactAgreement,
    employeeConsumer,
    forwardToReponseConso,
    status,
    vendor,
    tags
  ) <> (constructReport, extractReport.lift)
}

object ReportTables {
  val tables = TableQuery[ReportTable]
}

@Singleton
class ReportRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    accessTokenRepository: AccessTokenRepository,
    val companyRepository: CompanyRepository,
    val emailValidationRepository: EmailValidationRepository,
    configuration: Configuration
)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  val zoneId = ZoneId.of(configuration.get[String]("play.zoneId"))

  import dbConfig._

  implicit val ReportFileOriginColumnType =
    MappedColumnType.base[ReportFileOrigin, String](_.value, ReportFileOrigin(_))

  private class FileTable(tag: Tag) extends Table[ReportFile](tag, "report_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("report_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[String]("filename")
    def storageFilename = column[String]("storage_filename")
    def origin = column[ReportFileOrigin]("origin")
    def avOutput = column[Option[String]]("av_output")
    def report = foreignKey("report_files_fk", reportId, reportTableQuery)(_.id.?)

    type FileData = (UUID, Option[UUID], OffsetDateTime, String, String, ReportFileOrigin, Option[String])

    def constructFile: FileData => ReportFile = {
      case (id, reportId, creationDate, filename, storageFilename, origin, avOutput) =>
        ReportFile(id, reportId, creationDate, filename, storageFilename, origin, avOutput)
    }

    def extractFile: PartialFunction[ReportFile, FileData] = {
      case ReportFile(id, reportId, creationDate, filename, storageFilename, origin, avOutput) =>
        (id, reportId, creationDate, filename, storageFilename, origin, avOutput)
    }

    def * =
      (id, reportId, creationDate, filename, storageFilename, origin, avOutput) <> (constructFile, extractFile.lift)
  }

  val reportTableQuery = ReportTables.tables

  private val fileTableQuery = TableQuery[FileTable]

  private val companyTableQuery = CompanyTables.tables

  private val date = SimpleFunction.unary[OffsetDateTime, LocalDate]("date")

  private val substr = SimpleFunction.ternary[String, Int, Int, String]("substr")

  private val trunc = SimpleFunction.binary[String, OffsetDateTime, OffsetDateTime]("date_trunc")

  private val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

  private val array_to_string = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")

  val backofficeAdminStartDate = OffsetDateTime.of(
    LocalDate.parse(configuration.get[String]("play.stats.backofficeAdminStartDate")),
    LocalTime.MIDNIGHT,
    ZoneOffset.UTC
  )

  implicit class RegexLikeOps(s: Rep[String]) {
    def regexLike(p: Rep[String]): Rep[Boolean] = {
      val expr = SimpleExpression.binary[String, String, Boolean] { (s, p, qb) =>
        qb.expr(s)
        qb.sqlBuilder += " ~* "
        qb.expr(p)
      }
      expr.apply(s, p)
    }
  }

  def create(report: Report): Future[Report] = db
    .run(reportTableQuery += report)
    .map(_ => report)

  def list: Future[List[Report]] = db.run(reportTableQuery.to[List].result)

  def findByEmail(email: EmailAddress): Future[Seq[Report]] =
    db.run(reportTableQuery.filter(_.email === email).result)

  def update(report: Report): Future[Report] = {
    val queryReport =
      for (refReport <- reportTableQuery if refReport.id === report.id)
        yield refReport
    db.run(queryReport.update(report))
      .map(_ => report)
  }

  def count(siret: Option[SIRET] = None): Future[Int] = db
    .run(
      reportTableQuery
        .filterOpt(siret) { case (table, siret) =>
          table.companySiret === siret
        }
        .length
        .result
    )

  def monthlyCount: Future[List[MonthlyStat]] = db
    .run(
      reportTableQuery
        .filter(report => report.creationDate > OffsetDateTime.now().minusMonths(11).withDayOfMonth(1))
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map { case ((month, year), group) =>
          (month, year, group.length)
        }
        .to[List]
        .result
    )
    .map(_.map(result => MonthlyStat(result._3, YearMonth.of(result._2, result._1))))

  val baseStatReportTableQuery = reportTableQuery
    .filter(_.creationDate > backofficeAdminStartDate)
  val baseMonthlyStatReportTableQuery = baseStatReportTableQuery.filter(report =>
    report.creationDate > OffsetDateTime.now().minusMonths(11).withDayOfMonth(1)
  )

  def countWithStatus(
      statusList: List[ReportStatusValue],
      cutoff: Option[Duration],
      withWebsite: Option[Boolean] = None
  ) = db
    .run(
      baseStatReportTableQuery
        .filterIf(cutoff.isDefined)(_.creationDate < OffsetDateTime.now().minus(cutoff.get))
        .filter(_.status inSet statusList.map(_.defaultValue))
        .filterOpt(withWebsite) { case (table, withWebsite) =>
          table.websiteURL.isDefined === withWebsite
        }
        .length
        .result
    )

  def countMonthlyWithStatus(statusList: List[ReportStatusValue]): Future[List[MonthlyStat]] = db
    .run(
      baseMonthlyStatReportTableQuery
        .filter(_.status inSet statusList.map(_.defaultValue))
        .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
        .map { case ((month, year), group) =>
          (month, year, group.length)
        }
        .to[List]
        .result
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

  def getWithWebsites(): Future[List[Report]] = db.run {
    reportTableQuery
      .filter(_.websiteURL.isDefined)
      .to[List]
      .result
  }

  def getWithPhones(): Future[List[Report]] = db.run {
    reportTableQuery
      .filter(_.phone.isDefined)
      .to[List]
      .result
  }

  def getReportsStatusDistribution(companyId: UUID): Future[Map[String, Int]] =
    db.run(
      reportTableQuery
        .filter(_.companyId === companyId)
        .groupBy(_.status)
        .map { case (status, report) => status -> report.size }
        .result
    ).map(_.foldLeft(Map.empty[String, Int]) { case (acc, (a, b)) =>
      acc ++ Map(a -> b)
    })

  def getReportsTagsDistribution(companyId: UUID): Future[Map[String, Int]] = {
    def spreadListOfTags(map: Seq[(List[String], Int)]): Map[String, Int] =
      map
        .foldLeft(Map.empty[String, Int]) { case (acc, (tags, count)) =>
          acc ++ Map(tags.map(tag => tag -> (count + acc.getOrElse(tag, 0))): _*)
        }

    db.run(
      reportTableQuery
        .filter(_.companyId === companyId)
        .groupBy(_.tags)
        .map { case (status, report) => (status, report.size) }
        .sortBy(_._2.desc)
        .result
    ).map(spreadListOfTags)
  }

  def getReportsCountByDay(companyId: UUID): Future[Seq[(LocalDate, Int)]] =
    getReportsCount(companyId, "day", (date, i) => date.minusDays(i))

  def getReportsCountByWeek(companyId: UUID): Future[Seq[(LocalDate, Int)]] =
    getReportsCount(companyId, "week", (date, i) => date.minusWeeks(i))

  def getReportsCountByMonth(companyId: UUID): Future[Seq[(LocalDate, Int)]] =
    getReportsCount(companyId, "month", (date, i) => date.minusMonths(i))

  private[this] def getReportsCount(
      companyId: UUID,
      truncName: String,
      dateOperator: (LocalDate, Int) => LocalDate,
      tick: Int = 7
  ) =
    db.run(
      reportTableQuery
        .filter(_.companyId === companyId)
        .filter(
          _.creationDate >= ZonedDateTime
            .of(dateOperator(LocalDate.now(), tick), LocalTime.MIN, zoneId)
            .toOffsetDateTime
        )
        .groupBy(x => trunc(truncName, x.creationDate))
        .map { case (creationDate, report) => creationDate -> report.size }
        .result
    ).map(mapPeriod(tick, (date, i) => date.minusMonths(i)))

  private[this] def mapPeriod(
      ticks: Int,
      dateOperator: (LocalDate, Int) => LocalDate
  )(
      fetchedData: Seq[(OffsetDateTime, Int)]
  ): Seq[(LocalDate, Int)] = {
    val start = dateOperator(LocalDate.now(), ticks).atStartOfDay().withDayOfMonth(1).toLocalDate
    (1 to ticks).map { i =>
      val date = dateOperator(start, -i)
      val count = fetchedData
        .find(_._1.toLocalDate.equals(date))
        .map(_._2)
        .getOrElse(0)
      (date, count)
    }
  }

  def getHosts(companyId: UUID): Future[Seq[String]] =
    db.run(
      reportTableQuery
        .filter(_.companyId === companyId)
        .filter(_.host.isDefined)
        .map(_.host)
        .distinct
        .result
    ).map(_.map(_.getOrElse("")))

  def getReports(offset: Long, limit: Int, filter: ReportFilter): Future[PaginatedResult[Report]] = db.run {
    val query = reportTableQuery
      .filterOpt(filter.email) { case (table, email) =>
        table.email === EmailAddress(email)
      }
      .filterOpt(filter.websiteURL) { case (table, websiteURL) =>
        table.websiteURL.map(_.asColumnOf[String]) like s"%$websiteURL%"
      }
      .filterOpt(filter.phone) { case (table, reportedPhone) =>
        table.phone.map(_.asColumnOf[String]) like s"%$reportedPhone%"
      }
      .filterOpt(filter.websiteURL.flatMap(_ => None).orElse(filter.websiteExists)) { case (table, websiteRequired) =>
        table.websiteURL.isDefined === websiteRequired
      }
      .filterOpt(filter.phone.flatMap(_ => None).orElse(filter.phoneExists)) { case (table, phoneRequired) =>
        table.phone.isDefined === phoneRequired
      }
      .filterIf(filter.siretSirenList.nonEmpty) { case table =>
        table.companySiret
          .map(siret =>
            (siret inSetBind filter.siretSirenList.filter(_.matches(SIRET.pattern)).map(SIRET(_)).distinct) ||
              (substr(siret.asColumnOf[String], 0.bind, 10.bind) inSetBind filter.siretSirenList
                .filter(_.matches(SIREN.pattern))
                .distinct)
          )
          .getOrElse(false)
      }
      .filterOpt(filter.companyName) { case (table, companyName) =>
        table.companyName like s"${companyName}%"
      }
      .filterIf(filter.companyCountries.nonEmpty) { case table =>
        table.companyCountry
          .map(country => country.inSet(filter.companyCountries.map(Country.fromCode)))
          .getOrElse(false)
      }
      .filterOpt(filter.start) { case (table, start) =>
        table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, zoneId).toOffsetDateTime
      }
      .filterOpt(filter.end) { case (table, end) =>
        table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, zoneId).toOffsetDateTime
      }
      .filterOpt(filter.category) { case (table, category) =>
        table.category === category
      }
      .filterOpt(filter.hasCompany) { case (table, hasCompany) =>
        table.companyId.isDefined === hasCompany
      }
      .filterOpt(filter.statusList) { case (table, statusList) =>
        table.status.inSet(statusList.map(_.defaultValue))
      }
      .filterIf(!filter.tags.isEmpty) { case table =>
        table.tags @& filter.tags.toList.bind
      }
      .filterOpt(filter.details) { case (table, details) =>
        array_to_string(table.subcategories, ",", "") ++ array_to_string(table.details, ",", "") regexLike s"${details}"
      }
      .filterOpt(filter.employeeConsumer) { case (table, employeeConsumer) =>
        table.employeeConsumer === employeeConsumer
      }
      .joinLeft(companyTableQuery)
      .on(_.companyId === _.id)
      .filterIf(filter.departments.length > 0) { case (report, company) =>
        company.map(_.department).flatten.map(a => a.inSet(filter.departments)).getOrElse(false)
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
      hasNextPage = count - (offset + limit) > 0
    )
  }

  def getReportsByIds(ids: List[UUID]): Future[List[Report]] = db.run(
    reportTableQuery
      .filter(_.id inSet ids)
      .to[List]
      .result
  )

  def createFile(file: ReportFile): Future[ReportFile] = db
    .run(fileTableQuery += file)
    .map(_ => file)

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID) = {
    val queryFile =
      for (refFile <- fileTableQuery.filter(_.id.inSet(fileIds)))
        yield refFile.reportId
    db.run(queryFile.update(Some(reportId)))
  }

  def getFile(uuid: UUID): Future[Option[ReportFile]] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .to[List]
        .result
        .headOption
    )

  def retrieveReportFiles(reportId: UUID): Future[List[ReportFile]] = db
    .run(
      fileTableQuery
        .filter(_.reportId === reportId)
        .to[List]
        .result
    )

  def prefetchReportsFiles(reportsIds: List[UUID]): Future[Map[UUID, List[ReportFile]]] =
    db.run(
      fileTableQuery
        .filter(
          _.reportId inSetBind reportsIds
        )
        .to[List]
        .result
    ).map(events => events.groupBy(_.reportId.get))

  def deleteFile(uuid: UUID): Future[Int] = db
    .run(
      fileTableQuery
        .filter(_.id === uuid)
        .delete
    )

  def setAvOutput(fileId: UUID, output: String) = db
    .run(
      fileTableQuery
        .filter(_.id === fileId)
        .map(_.avOutput)
        .update(Some(output))
    )

  def getNbReportsGroupByCompany(offset: Long, limit: Int): Future[PaginatedResult[DeprecatedCompanyWithNbReports]] = {
    val q = db.run(
      companyTableQuery
        .joinLeft(reportTableQuery)
        .on(_.id === _.companyId)
        .groupBy(_._1)
        .map { case (grouped, all) => (grouped, all.map(_._2).size) }
        .to[List]
        .sortBy(_._2.desc)
        .result
    )

    for {
      res <- q.map(_.map { case (company, cnt) => DeprecatedCompanyWithNbReports(company, cnt) })
    } yield PaginatedResult(
      totalCount = res.length,
      entities = res.drop(offset.toInt).take(limit).toList,
      hasNextPage = res.length - (offset + limit) > 0
    )
  }

  def getByStatus(status: ReportStatusValue): Future[List[Report]] =
    db.run(reportTableQuery.filter(_.status === status.defaultValue).to[List].result)

  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]] = db
    .run(
      reportTableQuery
        .filter(_.status === ReportStatus.TRAITEMENT_EN_COURS.defaultValue)
        .filter(_.companyId inSet companiesIds)
        .to[List]
        .result
    )

  def getWebsiteReportsWithoutCompany(
      start: Option[LocalDate] = None,
      end: Option[LocalDate] = None
  ): Future[List[Report]] = db
    .run(
      reportTableQuery
        .filter(_.websiteURL.isDefined)
        .filter(_.companyId.isEmpty)
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, zoneId).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, zoneId).toOffsetDateTime
        }
        .to[List]
        .result
    )

  def getUnkonwnReportCountByHost(
      host: Option[String],
      start: Option[LocalDate] = None,
      end: Option[LocalDate] = None
  ): Future[List[(Option[String], Int)]] = db
    .run(
      reportTableQuery
        .filter(_.host.isDefined)
        .filter(t => host.fold(true.bind)(h => t.host.fold(true.bind)(_ like s"%${h}%")))
        .filter(_.companyId.isEmpty)
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, zoneId).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, zoneId).toOffsetDateTime
        }
        .groupBy(_.host)
        .map { case (host, report) => (host, report.size) }
        .sortBy(_._2.desc)
        .to[List]
        .result
    )

  def getPhoneReports(start: Option[LocalDate], end: Option[LocalDate]): Future[List[Report]] = db
    .run(
      reportTableQuery
        .filter(_.phone.isDefined)
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, zoneId).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, zoneId).toOffsetDateTime
        }
        .to[List]
        .result
    )
}
