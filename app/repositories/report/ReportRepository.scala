package repositories.report

import com.github.tminglei.slickpg.TsVector
import models._
import models.report._
import models.report.reportmetadata.ReportMetadata
import models.report.reportmetadata.ReportWithMetadata
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import repositories.report.ReportColumnType._
import repositories.report.ReportRepository.ReportOrdering
import repositories.report.ReportRepository.queryFilter
import repositories.reportconsumerreview.ResponseConsumerReviewColumnType._
import repositories.reportconsumerreview.ResponseConsumerReviewTable
import repositories.reportfile.ReportFileTable
import repositories.reportmetadata.ReportMetadataTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.Constants.Departments.toPostalCode
import utils._

import java.time._
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import scala.collection.SortedMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReportRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[ReportTable, Report]
    with ReportRepositoryInterface {

  override val table: TableQuery[ReportTable] = ReportTable.table
  import dbConfig._

  def findSimilarReportList(report: ReportDraft, after: OffsetDateTime): Future[List[Report]] = {

    val similarReportQuery = table
      .filter(_.email === report.email)
      .filterOpt(report.companyAddress.flatMap(_.postalCode))(_.companyPostalCode === _)
      .filterIf(report.companyAddress.flatMap(_.postalCode).isEmpty)(_.companyPostalCode.isEmpty)
      .filterOpt(report.companyAddress.flatMap(_.number))(_.companyStreetNumber === _)
      .filterIf(report.companyAddress.flatMap(_.number).isEmpty)(_.companyStreetNumber.isEmpty)
      .filterOpt(report.companyAddress.flatMap(_.street))(_.companyStreet === _)
      .filterIf(report.companyAddress.flatMap(_.street).isEmpty)(_.companyStreet.isEmpty)
      .filterOpt(report.companyAddress.flatMap(_.addressSupplement))(_.companyAddressSupplement === _)
      .filterIf(report.companyAddress.flatMap(_.addressSupplement).isEmpty)(_.companyAddressSupplement.isEmpty)
      .filterOpt(report.companyAddress.flatMap(_.city))(_.companyCity === _)
      .filterIf(report.companyAddress.flatMap(_.city).isEmpty)(_.companyCity.isEmpty)
      .filter(_.creationDate >= after)
      .filter(_.category === report.category)

    db.run(similarReportQuery.result).map(_.toList)
  }

  def reportsCountBySubcategories(
      userRole: UserRole,
      filters: ReportsCountBySubcategoriesFilter,
      lang: Locale
  ): Future[Seq[(String, List[String], Int, Int)]] = {
    implicit val localeColumnType = MappedColumnType.base[Locale, String](_.toLanguageTag, Locale.forLanguageTag)

    db.run(
      ReportTable
        .table(Some(userRole))
        .filterOpt(filters.start) { case (table, s) =>
          table.creationDate >= ZonedDateTime.of(s, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(filters.end) { case (table, e) =>
          table.creationDate < ZonedDateTime.of(e, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filter { table =>
          lang match {
            case Locale.FRENCH => table.lang === Locale.FRENCH || table.lang.isEmpty
            case _             => table.lang =!= Locale.FRENCH
          }
        }
        .filterIf(filters.departments.nonEmpty) { case (table) =>
          filters.departments
            .flatMap(toPostalCode)
            .map(dep => table.companyPostalCode.asColumnOf[String] like s"${dep}%")
            .reduceLeft(_ || _)
        }
        .groupBy(reportTable => (reportTable.category, reportTable.subcategories))
        .map { case ((category, subCategories), group) =>
          (
            category,
            subCategories,
            group.length,
            // Hack to be able to implement a filter clause with group by (possible with PG but not slick)
            // https://stackoverflow.com/questions/57372823/filter-in-select-using-slick
            // group.filter(reportTable => (ReportTag.ReponseConso: ReportTag).bind === reportTable.tags.any).length will not work (even if it should, an issue is open)
            group
              .map { reportTable =>
                Case If (ReportTag.ReponseConso: ReportTag).bind === reportTable.tags.any Then 1
              }
              .countGroupBy[Int]
          )
        }
        .result
    )
  }

  def countByDepartments(start: Option[LocalDate], end: Option[LocalDate]): Future[Seq[(String, Int)]] =
    db.run(
      table
        .filterOpt(start) { case (table, s) =>
          table.creationDate >= ZonedDateTime.of(s, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .filterOpt(end) { case (table, e) =>
          table.creationDate < ZonedDateTime.of(e, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
        }
        .groupBy(_.companyPostalCode.getOrElse(""))
        .map { case (department, group) => (department, group.length) }
        .result
    )

  def count(userRole: Option[UserRole], filter: ReportFilter): Future[Int] =
    db.run(queryFilter(ReportTable.table(userRole), filter).length.result)

  def getMonthlyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int = 7): Future[Seq[CountByDate]] =
    db
      .run(
        queryFilter(ReportTable.table(userRole), filter)
          .filter { case (report, _) =>
            report.creationDate > OffsetDateTime
              .now()
              .minusMonths(ticks.toLong)
              .withDayOfMonth(1)
          }
          .groupBy { case (report, _) =>
            (DatePartSQLFunction("month", report.creationDate), DatePartSQLFunction("year", report.creationDate))
          }
          .map { case ((month, year), group) => (month, year, group.length) }
          .result
      )
      .map(_.map { case (month, year, length) => CountByDate(length, LocalDate.of(year, month, 1)) })
      .map(fillFullPeriod(ticks, (x, i) => x.minusMonths(i.toLong).withDayOfMonth(1)))

  def getWeeklyCount(userRole: Option[UserRole], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]] =
    db.run(
      queryFilter(ReportTable.table(userRole), filter)
        .filter { case (report, _) => report.creationDate > OffsetDateTime.now().minusWeeks(ticks.toLong) }
        .groupBy { case (report, _) =>
          (DatePartSQLFunction("week", report.creationDate), DatePartSQLFunction("year", report.creationDate))
        }
        .map { case ((week, year), group) =>
          (week, year, group.length)
        }
        .result
    ).map(_.map { case (week, year, length) =>
      CountByDate(
        length,
        LocalDate
          .now()
          .`with`(WeekFields.ISO.weekBasedYear(), year.toLong)
          .`with`(WeekFields.ISO.weekOfWeekBasedYear(), week.toLong)
          .`with`(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue.toLong)
      )
    }).map(
      fillFullPeriod(
        ticks,
        (x, i) => x.minusWeeks(i.toLong).`with`(WeekFields.ISO.dayOfWeek(), DayOfWeek.MONDAY.getValue.toLong)
      )
    )

  def getDailyCount(
      userRole: Option[UserRole],
      filter: ReportFilter,
      ticks: Int
  ): Future[Seq[CountByDate]] = db
    .run(
      queryFilter(ReportTable.table(userRole), filter)
        .filter { case (report, _) => report.creationDate > OffsetDateTime.now().minusDays(11) }
        .groupBy { case (report, _) =>
          (
            DatePartSQLFunction("day", report.creationDate),
            DatePartSQLFunction("month", report.creationDate),
            DatePartSQLFunction("year", report.creationDate)
          )
        }
        .map { case ((day, month, year), group) =>
          (day, month, year, group.length)
        }
        .result
    )
    .map(_.map { case (day, month, year, length) => CountByDate(length, LocalDate.of(year, month, day)) })
    .map(fillFullPeriod(ticks, (x, i) => x.minusDays(i.toLong)))

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

  def getReports(companyId: UUID): Future[List[Report]] = db.run {
    table
      .filter(_.companyId === companyId)
      .to[List]
      .result
  }

  def getWithWebsites(): Future[List[Report]] = db.run {
    table
      .filter(_.websiteURL.isDefined)
      .to[List]
      .result
  }

  def getForWebsiteWithoutCompany(websiteHost: String): Future[List[UUID]] = db.run {
    table
      .filter(_.host === websiteHost)
      .filter(_.companySiret.isEmpty)
      .filter(_.status === ReportStatus.NA.entryName)
      .map(_.id)
      .to[List]
      .result
  }

  def getWithPhones(): Future[List[Report]] = db.run {
    table
      .filter(_.phone.isDefined)
      .to[List]
      .result
  }

  def getReportsStatusDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[String, Int]] =
    db.run(
      ReportTable
        .table(Some(userRole))
        .filterOpt(companyId)(_.companyId === _)
        .groupBy(_.status)
        .map { case (status, report) => status -> report.size }
        .result
    ).map(_.toMap)

  def getReportsTagsDistribution(companyId: Option[UUID], userRole: UserRole): Future[Map[ReportTag, Int]] = {
    def spreadListOfTags(map: Seq[(List[ReportTag], Int)]): Map[ReportTag, Int] =
      map.foldLeft(Map.empty[ReportTag, Int]) { case (acc, (tags, count)) =>
        acc ++ Map(tags.map(tag => tag -> (count + acc.getOrElse(tag, 0))): _*)
      }

    db.run(
      ReportTable
        .table(Some(userRole))
        .filterOpt(companyId)(_.companyId === _)
        .groupBy(_.tags)
        .map { case (status, report) => (status, report.size) }
        .sortBy(_._2.desc)
        .result
    ).map(spreadListOfTags)
  }

  def getHostsByCompany(companyId: UUID): Future[Seq[String]] =
    db.run(
      table
        .filter(_.companyId === companyId)
        .filter(_.host.isDefined)
        .map(_.host)
        .distinct
        .result
    ).map(_.map(_.getOrElse("")))

  def getReportsWithFiles(
      userRole: Option[UserRole],
      filter: ReportFilter
  ): Future[SortedMap[Report, List[ReportFile]]] =
    for {
      queryResult <- queryFilter(ReportTable.table(userRole), filter)
        .map { case (report, _) => report }
        .joinLeft(ReportFileTable.table)
        .on { case (report, reportFile) => report.id === reportFile.reportId }
        .sortBy { case (report, _) => report.creationDate.desc }
        .withPagination(db)(maybeOffset = Some(0), maybeLimit = Some(50000))
      res = queryResult.entities
        .groupBy(a => a._1)
        .view
        .mapValues { value =>
          value.flatMap(tuple => tuple._2)
        }
        .toSeq
      filesGroupedByReports = SortedMap(res: _*)(ReportOrdering)
    } yield filesGroupedByReports

  def getReports(
      userRole: Option[UserRole],
      filter: ReportFilter,
      offset: Option[Long] = None,
      limit: Option[Int] = None
  ): Future[PaginatedResult[ReportWithMetadata]] = for {
    reportsAndMetadatas <- queryFilter(ReportTable.table(userRole), filter)
      .sortBy { case (report, _) => report.creationDate.desc }
      .withPagination(db)(offset, limit)
    reportsWithMetadata = reportsAndMetadatas.mapEntities { case (report, maybeMetadata) =>
      ReportWithMetadata(report, maybeMetadata)
    }
  } yield reportsWithMetadata

  def getReportsByIds(ids: List[UUID]): Future[List[Report]] = db.run(
    table
      .filter(_.id inSet ids)
      .to[List]
      .result
  )

  def getByStatus(status: List[ReportStatus]): Future[List[Report]] =
    db.run(
      table
        .filter(_.status inSet status.map(_.entryName))
        .to[List]
        .result
    )

  override def getByStatusAndExpired(status: List[ReportStatus], now: OffsetDateTime): Future[List[Report]] =
    db.run(
      table
        .filter(_.status inSet status.map(_.entryName))
        .filter(_.expirationDate <= now)
        .to[List]
        .result
    )

  def getPendingReports(companiesIds: List[UUID]): Future[List[Report]] = db
    .run(
      table
        .filter(_.status === ReportStatus.TraitementEnCours.entryName)
        .filter(_.companyId inSet companiesIds)
        .to[List]
        .result
    )

  override def cloudWord(companyId: UUID): Future[List[ReportWordOccurrence]] =
    db.run(
      sql"""
        SELECT to_tsvector('french', STRING_AGG(replace(reportDetail.detailField,'Description : ',''), ''))
        FROM (
            SELECT unnest(details) as detailField
            FROM reports
            WHERE company_id = '#${companyId.toString}') as reportDetail
        WHERE reportDetail.detailField like 'Description%';
        """.as[TsVector]
    ).map { c =>
      val tsVector = c.headOption.filterNot(_ == null).getOrElse(TsVector.apply(""))
      tsVector.value.split(' ').toList.flatMap { arrayOfOccurences =>
        arrayOfOccurences.split(":").toList match {
          case word :: occurrences :: Nil =>
            List(
              ReportWordOccurrence(
                value = word.replace("\'", ""),
                count = occurrences.split(",").length
              )
            )
          case _ => List.empty[ReportWordOccurrence]
        }
      }
    }

  def getPhoneReports(start: Option[LocalDate], end: Option[LocalDate]): Future[List[Report]] =
    db
      .run(
        table
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

  override def getFor(userRole: Option[UserRole], id: UUID): Future[Option[Report]] =
    db.run(ReportTable.table(userRole).filter(_.id === id).result.headOption)
}

object ReportRepository {
  object ReportFileOrdering extends Ordering[Report] {
    def compare(a: Report, b: Report) =
      b.creationDate compareTo (a.creationDate)
  }

  object ReportOrdering extends Ordering[Report] {
    def compare(a: Report, b: Report) =
      (b.creationDate compareTo (a.creationDate)) match {
        case 0 => b.id compareTo (a.id)
        case c => c
      }
  }

  object ReportWithMetadataOrdering extends Ordering[ReportWithMetadata] {
    def compare(a: ReportWithMetadata, b: ReportWithMetadata) =
      ReportOrdering.compare(a.report, b.report)

  }

  implicit class RegexLikeOps(s: Rep[String]) {
    def regexLike(p: Rep[String]): Rep[Boolean] = {
      val expr = SimpleExpression.binary[String, String, Boolean] { (s, p, qb) =>
        qb.expr(s)
        qb.sqlBuilder += " ~* ": Unit
        qb.expr(p)
      }
      expr.apply(s, p)
    }
  }

  def orFilter(table: Query[ReportTable, Report, Seq], filter: PreFilter): Query[ReportTable, Report, Seq] = {
    val default = LiteralColumn(1) === LiteralColumn(1)

    table.filter { table =>
      List(
        if (filter.tags.isEmpty) None else Some(table.tags @& filter.tags.bind),
        filter.category.map(category => table.category === category.entryName)
      ).collect { case Some(f) => f }.reduceLeftOption(_ || _).getOrElse(default)
    }
  }

  def queryFilter(
      table: Query[ReportTable, Report, Seq],
      filter: ReportFilter
  ): Query[(ReportTable, Rep[Option[ReportMetadataTable]]), (Report, Option[ReportMetadata]), Seq] = {
    implicit val localeColumnType = MappedColumnType.base[Locale, String](_.toLanguageTag, Locale.forLanguageTag)

    table
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
            (siret inSetBind filter.siretSirenList
              .filter(_.matches(SIRET.pattern))
              .map(SIRET.fromUnsafe(_))
              .distinct) ||
              (SubstrSQLFunction(siret.asColumnOf[String], 0.bind, 10.bind) inSetBind filter.siretSirenList
                .filter(_.matches(SIREN.pattern))
                .distinct)
          )
          .getOrElse(false)
      }
      .filterOpt(filter.siretSirenDefined) { case (table, siretSirenDefined) =>
        if (siretSirenDefined) table.companySiret.nonEmpty else table.companySiret.isEmpty
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
        table.creationDate >= start
      }
      .filterOpt(filter.end) { case (table, end) =>
        table.creationDate <= end
      }
      .filterOpt(filter.category) { case (table, category) =>
        // Condition pour récupérer les achats en sur internet soit dans la nouvelle catégorie,
        // soit dans l'ancienne catégorie à condition que le signalement ait un tag "Internet"
        if (ReportCategory.withName(category) == ReportCategory.AchatInternet) {
          table.category === category ||
          (
            table.category === ReportCategory.AchatMagasinInternet.entryName
              && table.tags @> List[ReportTag](ReportTag.Internet).bind
          )
          // Condition pour récupérer les achats en magasin soit dans la nouvelle catégorie,
          // soit dans l'ancienne catégorie à condition que le signalement n'ait pas de tag "Internet"
        } else if (ReportCategory.withName(category) == ReportCategory.AchatMagasin) {
          table.category === category ||
          (
            table.category === ReportCategory.AchatMagasinInternet.entryName
              && !(table.tags @> List[ReportTag](ReportTag.Internet).bind)
          )
        } else {
          table.category === category
        }
      }
      .filterIf(filter.status.nonEmpty) { case table =>
        table.status.inSet(filter.status.map(_.entryName))
      }
      .filterIf(filter.withTags.nonEmpty) { table =>
        table.tags @& filter.withTags.toList.bind
      }
      .filterNot { table =>
        table.tags @& filter.withoutTags.toList.bind
      }
      .filterOpt(filter.details) { case (table, details) =>
        ArrayToStringSQLFunction(table.subcategories, ",", "") ++ " " ++
          ArrayToStringSQLFunction(table.details, ",", "") ++ " " ++
          table.influencerName.getOrElse("") regexLike s"$details"
      }
      .filterOpt(filter.description) { case (table, description) =>
        // unique separator use to match the string between  "Description :" et and separator
        val uniqueSeparator = UUID.randomUUID().toString
        ArrayToStringSQLFunction(
          table.details,
          uniqueSeparator,
          ""
        ) regexLike s".*Description :.*$description.*$uniqueSeparator"
      }
      .filterOpt(filter.employeeConsumer) { case (table, employeeConsumer) =>
        table.employeeConsumer === employeeConsumer
      }
      .filterOpt(filter.contactAgreement) { case (table, contactAgreement) =>
        table.contactAgreement === contactAgreement
      }
      .filterOpt(filter.hasAttachment) { case (table, hasAttachment) =>
        val exists = ReportFileTable.table
          .filter(x => x.reportId === table.id)
          .map(_.reportId)
          .exists
        if (hasAttachment) exists else !exists
      }
      .filterOpt(filter.hasEvaluation) { case (table, hasEvaluation) =>
        val exists = ResponseConsumerReviewTable.table
          .filter(x => x.reportId === table.id)
          .map(_.reportId)
          .exists
        if (hasEvaluation) exists else !exists
      }
      .filterIf(filter.evaluation.nonEmpty) { table =>
        ResponseConsumerReviewTable.table
          .filter(_.reportId === table.id)
          .filter(_.evaluation.inSet(filter.evaluation))
          .exists
      }
      .filterIf(filter.departments.nonEmpty) { case (table) =>
        val departmentsFilter: Rep[Boolean] = filter.departments
          .flatMap(toPostalCode)
          .map(dep => table.companyPostalCode.asColumnOf[String] like s"${dep}%")
          .reduceLeft(_ || _)
        // Avoid searching departments in foreign countries
        departmentsFilter && table.companyCountry.isEmpty

      }
      .filterIf(filter.activityCodes.nonEmpty) { case (table) =>
        table.companyActivityCode.inSetBind(filter.activityCodes).getOrElse(false)
      }
      .filterOpt(filter.visibleToPro) { case (table, visibleToPro) =>
        table.visibleToPro === visibleToPro
      }
      .filterOpt(filter.isForeign) { case (table, isForeign) =>
        if (isForeign) table.lang =!= Locale.FRENCH else table.lang === Locale.FRENCH || table.lang.isEmpty
      }
      .filterOpt(filter.hasBarcode) { case (table, barcodeRequired) =>
        table.barcodeProductId.isDefined === barcodeRequired
      }
      .filterOpt(filter.fullText) { case (table, fullText) =>
        table.contactAgreement &&
        toTsVector(
          table.firstName ++ " " ++ table.lastName ++ " " ++ table.consumerReferenceNumber.asColumnOf[String]
        ) @@ plainToTsQuery(fullText)
      }
      .joinLeft(ReportMetadataTable.table)
      .on(_.id === _.reportId)
      .filterOpt(filter.assignedUserId) { case ((_, maybeMetadataTable), assignedUserid) =>
        maybeMetadataTable.flatMap(_.assignedUserId) === assignedUserid
      }

  }

}
