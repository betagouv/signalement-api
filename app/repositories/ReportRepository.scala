package repositories

import models.report.DetailInputValue.toDetailInputValue
import models.report
import models._
import models.report.Report
import models.report.ReportFile
import models.report.ReportFileOrigin
import models.report.ReportFilter
import models.report.ReportStatus
import models.report.ReportTag
import models.report.WebsiteURL
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import repositories.mapping.Report._
import slick.jdbc.JdbcProfile
import utils.Constants.Departments.toPostalCode
import utils._

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
  def consumerPhone = column[Option[String]]("consumer_phone")
  def contactAgreement = column[Boolean]("contact_agreement")
  def employeeConsumer = column[Boolean]("employee_consumer")
  def forwardToReponseConso = column[Boolean]("forward_to_reponseconso")
  def status = column[String]("status")
  def vendor = column[Option[String]]("vendor")
  def tags = column[List[ReportTag]]("tags")
  def reponseconsoCode = column[List[String]]("reponseconso_code")
  def ccrfCode = column[List[String]]("ccrf_code")

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
      Option[String],
      Boolean,
      Boolean,
      Boolean,
      String,
      Option[String],
      List[ReportTag],
      List[String],
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
          consumerPhone,
          contactAgreement,
          employeeConsumer,
          forwardToReponseConso,
          status,
          vendor,
          tags,
          reponseconsoCode,
          ccrfCode
        ) =>
      report.Report(
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
        consumerPhone = consumerPhone,
        contactAgreement = contactAgreement,
        employeeConsumer = employeeConsumer,
        forwardToReponseConso = forwardToReponseConso,
        status = ReportStatus.withName(status),
        vendor = vendor,
        tags = tags,
        reponseconsoCode = reponseconsoCode,
        ccrfCode = ccrfCode
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
      r.consumerPhone,
      r.contactAgreement,
      r.employeeConsumer,
      r.forwardToReponseConso,
      r.status.entryName,
      r.vendor,
      r.tags,
      r.reponseconsoCode,
      r.ccrfCode
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
    consumerPhone,
    contactAgreement,
    employeeConsumer,
    forwardToReponseConso,
    status,
    vendor,
    tags,
    reponseconsoCode,
    ccrfCode
  ) <> (constructReport, extractReport.lift)
}

object ReportTables {
  val tables = TableQuery[ReportTable]
}

@Singleton
class ReportRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    val companyRepository: CompanyRepository,
    val emailValidationRepository: EmailValidationRepository
)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  implicit val ReportFileOriginColumnType =
    MappedColumnType.base[ReportFileOrigin, String](_.value, ReportFileOrigin(_))

  val reportTableQuery = ReportTables.tables

  private class FileTable(tag: Tag) extends Table[ReportFile](tag, "report_files") {

    def id = column[UUID]("id", O.PrimaryKey)
    def reportId = column[Option[UUID]]("report_id")
    def creationDate = column[OffsetDateTime]("creation_date")
    def filename = column[String]("filename")
    def storageFilename = column[String]("storage_filename")
    def origin = column[ReportFileOrigin]("origin")
    def avOutput = column[Option[String]]("av_output")
    def report = foreignKey("report_files_fk", reportId, ReportTables.tables)(_.id.?)

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

  private val fileTableQuery = TableQuery[FileTable]

  private val substr = SimpleFunction.ternary[String, Int, Int, String]("substr")

  private val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

  private val array_to_string = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")
  SimpleFunction.binary[List[ReportTag], Int, Int]("array_length")

  private[this] def queryFilter(filter: ReportFilter): Query[ReportTable, Report, Seq] =
    reportTableQuery
      .filterOpt(filter.email) { case (table, email) =>
        table.email === EmailAddress(email)
      }
      .filterOpt(filter.websiteURL) { case (table, websiteURL) =>
        table.websiteURL.map(_.asColumnOf[String]) like s"%$websiteURL%"
      }
      .filterOpt(filter.phone) { case (table, reportedPhone) =>
        table.phone.map(_.asColumnOf[String]) like s"%$reportedPhone%"
      }
      .filterOpt(filter.hasWebsite) { case (table, websiteRequired) =>
        table.websiteURL.isDefined === websiteRequired
      }
      .filterOpt(filter.hasPhone) { case (table, phoneRequired) =>
        table.phone.isDefined === phoneRequired
      }
      .filterOpt(filter.hasCompany) { case (table, hasCompany) =>
        table.companyId.isDefined === hasCompany
      }
      .filterOpt(filter.hasForeignCountry) { case (table, hasForeignCountry) =>
        table.companyCountry.isDefined === hasForeignCountry
      }
      .filterIf(filter.companyIds.nonEmpty)(_.companyId.map(_.inSetBind(filter.companyIds)).getOrElse(false))
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
        table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
      }
      .filterOpt(filter.end) { case (table, end) =>
        table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
      }
      .filterOpt(filter.category) { case (table, category) =>
        table.category === category
      }
      .filterIf(filter.status.nonEmpty) { case table =>
        table.status.inSet(filter.status.map(_.entryName))
      }
      .filterIf(filter.tags.nonEmpty) { table =>
        val nonEmptyReportTag = ReportTag.reportTagFrom(filter.tags)
        val includeNotTaggedReports = filter.tags.contains(report.ReportTagFilter.NA)
        filterTags(nonEmptyReportTag, includeNotTaggedReports, table)
      }
      .filterOpt(filter.details) { case (table, details) =>
        array_to_string(table.subcategories, ",", "") ++ array_to_string(
          table.details,
          ",",
          ""
        ) regexLike s"${details}"
      }
      .filterOpt(filter.employeeConsumer) { case (table, employeeConsumer) =>
        table.employeeConsumer === employeeConsumer
      }
      .filterIf(filter.departments.nonEmpty) { case (table) =>
        filter.departments
          .flatMap(toPostalCode)
          .map(dep => table.companyPostalCode.asColumnOf[String] like s"${dep}%")
          .reduceLeft(_ || _)
      }
      .joinLeft(CompanyTables.tables)
      .on(_.companyId === _.id)
      .filterIf(filter.activityCodes.nonEmpty)(
        _._2.map(_.activityCode).flatten.inSetBind(filter.activityCodes).getOrElse(false)
      )
      .map(_._1)

  private def filterTags(tags: Seq[ReportTag], includeUntaggedReports: Boolean, table: ReportTable) = {
    val includeNotTaggedReportsFilter: Rep[Boolean] = table.tags === List.empty[ReportTag].bind
    val includeTaggedReportFilter: Rep[Boolean] = table.tags @& tags.toList.bind

    val includeTaggedReport = tags.nonEmpty
    (includeTaggedReport, includeUntaggedReports) match {
      case (true, true)  => includeTaggedReportFilter || includeNotTaggedReportsFilter
      case (false, true) => includeNotTaggedReportsFilter
      case (true, false) => includeTaggedReportFilter
      case _             => true.bind
    }

  }

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

  def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]] =
    db.run(
      reportTableQuery
        .filterOpt(start) { case (table, s) =>
          table.creationDate >= ZonedDateTime.of(s, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(end) { case (table, e) =>
          table.creationDate < ZonedDateTime.of(e, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .groupBy(_.companyPostalCode.map(x => substr(x, 1, 2)).getOrElse(""))
        .map { case (department, group) => (department, group.length) }
        .sortBy(_._2.desc)
        .result
    )

  def update(report: Report): Future[Report] = {
    val queryReport =
      for (refReport <- reportTableQuery if refReport.id === report.id)
        yield refReport
    db.run(queryReport.update(report))
      .map(_ => report)
  }

  def count(filter: ReportFilter): Future[Int] = db.run(queryFilter(filter).length.result)

  def getMonthlyCount(filter: ReportFilter, ticks: Int = 7): Future[Seq[CountByDate]] =
    db
      .run(
        queryFilter(filter)
          .filter(report =>
            report.creationDate > OffsetDateTime.now(ZoneOffset.UTC).minusMonths(ticks).withDayOfMonth(1)
          )
          .groupBy(report => (date_part("month", report.creationDate), date_part("year", report.creationDate)))
          .map { case ((month, year), group) => (month, year, group.length) }
          .result
      )
      .map(_.map { case (month, year, length) => CountByDate(length, LocalDate.of(year, month, 1)) })
      .map(fillFullPeriod(ticks, (x, i) => x.minusMonths(i).withDayOfMonth(1)))

  def getDailyCount(
      filter: ReportFilter,
      ticks: Int
  ): Future[Seq[CountByDate]] = db
    .run(
      queryFilter(filter)
        .filter(report => report.creationDate > OffsetDateTime.now(ZoneOffset.UTC).minusDays(11))
        .groupBy(report =>
          (
            date_part("day", report.creationDate),
            date_part("month", report.creationDate),
            date_part("year", report.creationDate)
          )
        )
        .map { case ((day, month, year), group) =>
          (day, month, year, group.length)
        }
        .result
    )
    .map(_.map { case (day, month, year, length) => CountByDate(length, LocalDate.of(year, month, day)) })
    .map(fillFullPeriod(ticks, (x, i) => x.minusDays(i)))

  private[this] def fillFullPeriod(
      ticks: Int,
      dateOperator: (LocalDate, Int) => LocalDate
  )(
      fetchedData: Seq[CountByDate]
  ): Seq[CountByDate] = {
    val start = dateOperator(LocalDate.now(), ticks).atStartOfDay().toLocalDate
    val res = (1 to ticks).map { i =>
      val date = dateOperator(start, -i)
      val count = fetchedData
        .find(_.date.equals(date))
        .map(_.count)
        .getOrElse(0)
      CountByDate(count, date)
    }
    res
  }

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

  def getReportsStatusDistribution(companyId: Option[UUID]): Future[Map[String, Int]] =
    db.run(
      reportTableQuery
        .filterOpt(companyId)(_.companyId === _)
        .groupBy(_.status)
        .map { case (status, report) => status -> report.size }
        .result
    ).map(_.toMap)

  def getReportsTagsDistribution(companyId: Option[UUID]): Future[Map[ReportTag, Int]] = {
    def spreadListOfTags(map: Seq[(List[ReportTag], Int)]): Map[ReportTag, Int] =
      map.foldLeft(Map.empty[ReportTag, Int]) { case (acc, (tags, count)) =>
        acc ++ Map(tags.map(tag => tag -> (count + acc.getOrElse(tag, 0))): _*)
      }

    db.run(
      reportTableQuery
        .filterOpt(companyId)(_.companyId === _)
        .groupBy(_.tags)
        .map { case (status, report) => (status, report.size) }
        .sortBy(_._2.desc)
        .result
    ).map(spreadListOfTags)
  }

  def getHostsByCompany(companyId: UUID): Future[Seq[String]] =
    db.run(
      reportTableQuery
        .filter(_.companyId === companyId)
        .filter(_.host.isDefined)
        .map(_.host)
        .distinct
        .result
    ).map(_.map(_.getOrElse("")))

  def getReports(
      filter: ReportFilter,
      offset: Option[Long] = None,
      limit: Option[Int] = None
  ): Future[PaginatedResult[Report]] =
    queryFilter(filter)
      .sortBy(_.creationDate.desc)
      .withPagination(db)(offset, limit)

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

  def getByStatus(status: ReportStatus): Future[List[Report]] =
    db.run(reportTableQuery.filter(_.status === status.entryName).to[List].result)

  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]] = db
    .run(
      reportTableQuery
        .filter(_.status === ReportStatus.TraitementEnCours.entryName)
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
        .filter(x => x.companyId.isEmpty || x.companyCountry.isEmpty)
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
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
        .filter(x => x.companyId.isEmpty && x.companyCountry.isEmpty)
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
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
          table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .to[List]
        .result
    )
}
