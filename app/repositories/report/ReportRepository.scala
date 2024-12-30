package repositories.report

import com.github.tminglei.slickpg.TsVector
import models._
import models.barcode.BarcodeProduct
import models.company.Company
import models.report.ReportResponseType.ACCEPTED
import models.report.ReportStatus.SuppressionRGPD
import models.report._
import models.report.reportmetadata.ReportMetadata
import models.report.reportmetadata.ReportWithMetadataAndBookmark
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.PostgresProfile.api._
import repositories.barcode.BarcodeProductTable
import repositories.bookmark.Bookmark
import repositories.bookmark.BookmarkTable
import repositories.company.CompanyTable
import repositories.report.ReportColumnType._
import repositories.report.ReportRepository.ReportOrdering
import repositories.report.ReportRepository.queryFilter
import repositories.reportconsumerreview.ResponseConsumerReviewColumnType._
import repositories.reportconsumerreview.ResponseConsumerReviewTable
import repositories.reportengagementreview.ReportEngagementReviewTable
import repositories.reportfile.ReportFileTable
import repositories.reportmetadata.ReportMetadataTable
import repositories.reportresponse.ReportResponseTable
import repositories.CRUDRepository
import repositories.PaginateOps
import repositories.subcategorylabel.SubcategoryLabel
import repositories.subcategorylabel.SubcategoryLabelTable
import slick.basic.DatabaseConfig
import slick.basic.DatabasePublisher
import slick.jdbc.JdbcProfile
import slick.jdbc.ResultSetConcurrency
import slick.jdbc.ResultSetType
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

  // To stream PG properly, some parameters are required, see https://scala-slick.org/doc/stable/dbio.html
  def streamReports: Source[Report, NotUsed] = Source
    .fromPublisher(
      db.stream(
        table.result
          .withStatementParameters(
            rsType = ResultSetType.ForwardOnly,
            rsConcurrency = ResultSetConcurrency.ReadOnly,
            fetchSize = 10000
          )
          .transactionally
      )
    )
    .log("user")

  // To stream PG properly, some parameters are required, see https://scala-slick.org/doc/stable/dbio.html
  override def streamAll
      : DatabasePublisher[(((Report, Option[Company]), Option[BarcodeProduct]), Option[SubcategoryLabel])] = db.stream(
    table
      .joinLeft(CompanyTable.table)
      .on { case (report, company) => report.companyId === company.id }
      .joinLeft(BarcodeProductTable.table)
      .on { case ((report, _), product) => report.barcodeProductId === product.id }
      .joinLeft(SubcategoryLabelTable.table)
      .on { case (((report, _), _), subcategoryLabel) =>
        report.category === subcategoryLabel.category && report.subcategories === subcategoryLabel.subcategories
      }
      .result
      .withStatementParameters(
        rsType = ResultSetType.ForwardOnly,
        rsConcurrency = ResultSetConcurrency.ReadOnly,
        fetchSize = 10000
      )
      .transactionally
  )

  def findSimilarReportList(
      report: ReportDraft,
      after: OffsetDateTime,
      extendedEmailComparison: Boolean
  ): Future[List[Report]] = {

    val similarReportQuery =
      if (extendedEmailComparison) {
        val emailAddressSplitted = report.email.split
        table
          .filter(_.category === report.category)
          .filterOpt(report.companySiret)((report, siret) =>
            SubstrSQLFunction(report.companySiret.asColumnOf[String], 0.bind, 10.bind) === SIREN.fromSIRET(siret).value
          )
          .filterIf(report.companySiret.isEmpty)(_.companySiret.isEmpty)
          .filter(_.creationDate >= after)
          .result
          // We do this check after because we want to compare 'root' addresses for gmail (without . and +)
          .map(_.filter(report => emailAddressSplitted.isEquivalentTo(report.email.value)))
      } else {
        table
          .filter(_.email === report.email)
          .filter(_.category === report.category)
          .filterOpt(report.companySiret)(_.companySiret === _)
          .filterIf(report.companySiret.isEmpty)(_.companySiret.isEmpty)
          .filterOpt(report.companyAddress.flatMap(_.postalCode))(_.companyPostalCode === _)
          .filterIf(report.companyAddress.flatMap(_.postalCode).isEmpty)(_.companyPostalCode.isEmpty)
          .filter(_.creationDate >= after)
          .result
      }

    db.run(similarReportQuery.map(_.toList))
  }

  def reportsCountBySubcategories(
      user: User,
      filters: ReportsCountBySubcategoriesFilter,
      lang: Locale
  ): Future[Seq[(String, List[String], Int, Int)]] = {
    implicit val localeColumnType = MappedColumnType.base[Locale, String](_.toLanguageTag, Locale.forLanguageTag)

    db.run(
      ReportTable
        .table(Some(user))
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
        .filterIf(filters.departments.nonEmpty) { table =>
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

  def count(user: Option[User], filter: ReportFilter): Future[Int] =
    db.run(queryFilter(ReportTable.table(user), filter, user).length.result)

  def getMonthlyCount(user: Option[User], filter: ReportFilter, ticks: Int = 7): Future[Seq[CountByDate]] =
    db
      .run(
        queryFilter(ReportTable.table(user), filter, user)
          .filter { case (report, _, _, _) =>
            report.creationDate > OffsetDateTime
              .now()
              .minusMonths(ticks.toLong)
              .withDayOfMonth(1)
          }
          .groupBy { case (report, _, _, _) =>
            (DatePartSQLFunction("month", report.creationDate), DatePartSQLFunction("year", report.creationDate))
          }
          .map { case ((month, year), group) => (month, year, group.length) }
          .result
      )
      .map(_.map { case (month, year, length) => CountByDate(length, LocalDate.of(year, month, 1)) })
      .map(fillFullPeriod(ticks, (x, i) => x.minusMonths(i.toLong).withDayOfMonth(1)))

  def getWeeklyCount(user: Option[User], filter: ReportFilter, ticks: Int): Future[Seq[CountByDate]] =
    db.run(
      queryFilter(ReportTable.table(user), filter, user)
        .filter { case (report, _, _, _) => report.creationDate > OffsetDateTime.now().minusWeeks(ticks.toLong) }
        .groupBy { case (report, _, _, _) =>
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
      user: Option[User],
      filter: ReportFilter,
      ticks: Int
  ): Future[Seq[CountByDate]] = db
    .run(
      queryFilter(ReportTable.table(user), filter, user)
        .filter { case (report, _, _, _) => report.creationDate > OffsetDateTime.now().minusDays(11) }
        .groupBy { case (report, _, _, _) =>
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

  def getForWebsiteWithoutCompany(websiteHost: String): Future[List[Report]] = db.run {
    table
      .filter(_.host === websiteHost)
      .filter(_.companySiret.isEmpty)
      .filter(_.status === ReportStatus.NA.entryName)
      .to[List]
      .result
  }

  def getWithPhones(): Future[List[Report]] = db.run {
    table
      .filter(_.phone.isDefined)
      .to[List]
      .result
  }

  def getReportsStatusDistribution(companyId: Option[UUID], user: User): Future[Map[String, Int]] =
    db.run(
      ReportTable
        .table(Some(user))
        .filterOpt(companyId)(_.companyId === _)
        .groupBy(_.status)
        .map { case (status, report) => status -> report.size }
        .result
    ).map(_.toMap)

  def getAcceptedResponsesDistribution(companyId: UUID, user: User): Future[Map[ExistingResponseDetails, Int]] =
    db.run(
      ReportTable
        .table(Some(user))
        .join(ReportResponseTable.table)
        .on(_.id === _.reportId)
        .filter(_._2.responseType === ACCEPTED.entryName)
        .filter(_._2.responseDetails.isDefined)
        .filter(_._2.companyId === companyId)
        .groupBy(_._2.responseDetails)
        .map { case (details, group) => details -> group.size }
        .result
    ).map(
      _.toMap
        .map { case (details, nb) => ExistingResponseDetails.withName(details.get) -> nb }
    )

  def getReportsTagsDistribution(companyId: Option[UUID], user: User): Future[Map[ReportTag, Int]] = {
    def spreadListOfTags(map: Seq[(List[ReportTag], Int)]): Map[ReportTag, Int] =
      map.foldLeft(Map.empty[ReportTag, Int]) { case (acc, (tags, count)) =>
        acc ++ Map(tags.map(tag => tag -> (count + acc.getOrElse(tag, 0))): _*)
      }

    db.run(
      ReportTable
        .table(Some(user))
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
      user: Option[User],
      filter: ReportFilter
  ): Future[SortedMap[Report, List[ReportFile]]] =
    for {
      queryResult <- queryFilter(ReportTable.table(user), filter, user)
        .map { case (report, _, _, _) => report }
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

  private def sortReport(
      report: ReportTable,
      sortBy: Option[ReportSort],
      orderBy: Option[SortOrder]
  ) =
    (sortBy, orderBy) match {
      case (Some(ReportSort.Siret), Some(SortOrder.Asc))         => report.companySiret.asc
      case (Some(ReportSort.Siret), Some(SortOrder.Desc))        => report.companySiret.desc
      case (Some(ReportSort.CreationDate), Some(SortOrder.Asc))  => report.creationDate.asc
      case (Some(ReportSort.CreationDate), Some(SortOrder.Desc)) => report.creationDate.desc
      case _                                                     => report.creationDate.desc
    }

  def getReports(
      user: Option[User],
      filter: ReportFilter,
      offset: Option[Long],
      limit: Option[Int],
      sortBy: Option[ReportSort],
      orderBy: Option[SortOrder]
  ): Future[PaginatedResult[ReportWithMetadataAndBookmark]] = for {
    reportsAndMetadatas <- queryFilter(ReportTable.table(user), filter, user)
      .sortBy { case (report, _, _, _) => sortReport(report, sortBy, orderBy) }
      .withPagination(db)(offset, limit)
    reportsWithMetadata = reportsAndMetadatas.mapEntities {
      case (
            report: Report,
            metadata: Option[ReportMetadata],
            bookmark: Option[Bookmark],
            subcategoryLabel: Option[SubcategoryLabel]
          ) =>
        ReportWithMetadataAndBookmark.from(report, metadata, bookmark, subcategoryLabel)
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

  def getOldReportsNotRgpdDeleted(createdBefore: OffsetDateTime): Future[List[Report]] = db.run(
    table
      .filter(_.creationDate < createdBefore)
      .filter(_.status =!= SuppressionRGPD.entryName)
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

  def getPhoneReports(
      q: Option[String],
      start: Option[LocalDate],
      end: Option[LocalDate],
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[((Option[String], Option[SIRET], Option[String], String), Int)]] =
    table
      .filter(_.phone.isDefined)
      .filterOpt(q) { case (table, p) =>
        table.phone.like(s"%$p%")
      }
      .filterOpt(start) { case (table, start) =>
        table.creationDate >= ZonedDateTime.of(start, LocalTime.MIN, ZoneOffset.UTC.normalized()).toOffsetDateTime
      }
      .filterOpt(end) { case (table, end) =>
        table.creationDate < ZonedDateTime.of(end, LocalTime.MAX, ZoneOffset.UTC.normalized()).toOffsetDateTime
      }
      .groupBy(a => (a.phone, a.companySiret, a.companyName, a.category))
      .map { case (a, b) => (a, b.length) }
      .sortBy(_._2.desc)
      .withPagination(db)(
        maybeOffset = offset,
        maybeLimit = limit,
        maybePreliminaryAction = None
      )

  override def getFor(user: Option[User], id: UUID): Future[Option[ReportWithMetadataAndBookmark]] =
    for {
      maybeTuple <- db.run(
        ReportTable
          .table(user)
          .filter(_.id === id)
          .joinLeft(ReportMetadataTable.table)
          .on(_.id === _.reportId)
          .joinLeft(BookmarkTable.table)
          .on { case ((report, _), bookmark) =>
            bookmark.userId === user.map(_.id) && report.id === bookmark.reportId
          }
          .joinLeft(SubcategoryLabelTable.table)
          .on { case (((report, _), _), subcategoryLabel) =>
            report.category === subcategoryLabel.category && report.subcategories === subcategoryLabel.subcategories
          }
          .map { case (((report, metadata), bookmark), subcategoryLabel) =>
            (report, metadata, bookmark, subcategoryLabel)
          }
          .result
          .headOption
      )
      maybeReportWithMetadata = maybeTuple.map {
        case (
              report: Report,
              metadata: Option[ReportMetadata],
              bookmark: Option[Bookmark],
              subcategoryLabel: Option[SubcategoryLabel]
            ) =>
          ReportWithMetadataAndBookmark.from(report, metadata, bookmark, subcategoryLabel)
      }
    } yield maybeReportWithMetadata

  override def getLatestReportsOfCompany(companyId: UUID, limit: Int): Future[List[Report]] =
    db.run(
      table
        .filter(_.companyId.filter(_ === companyId).isDefined)
        .sortBy(_.creationDate.desc)
        .take(limit)
        .to[List]
        .result
    )

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

  // Utilisé pour caster un type mal généré par slick : @>
  // Cette fonction attend le même type à gauche et à droite
  // mais slick génère varchar[] @> text[]
  private val castVarCharArrayToTextArray = SimpleExpression.unary[List[String], List[String]] { (s, qb) =>
    qb.sqlBuilder += "CAST( ": Unit
    qb.expr(s)
    qb.sqlBuilder += " AS TEXT[])": Unit
  }

  def queryFilter(
      table: Query[ReportTable, Report, Seq],
      filter: ReportFilter,
      maybeUser: Option[User]
  ): Query[
    (ReportTable, Rep[Option[ReportMetadataTable]], Rep[Option[BookmarkTable]], Rep[Option[SubcategoryLabelTable]]),
    (Report, Option[ReportMetadata], Option[Bookmark], Option[SubcategoryLabel]),
    Seq
  ] = {
    implicit val localeColumnType = MappedColumnType.base[Locale, String](_.toLanguageTag, Locale.forLanguageTag)

    table
      .filterOpt(filter.email) { case (table, email) =>
        table.email === EmailAddress(email)
      }
      .filterOpt(filter.websiteURL) { case (table, websiteURL) =>
        table.websiteURL.map(_.asColumnOf[String]) like s"%$websiteURL%"
      }
      .filterOpt(filter.hasWebsite) { case (table, websiteRequired) =>
        table.websiteURL.isDefined === websiteRequired
      }
      .filterOpt(filter.phone) { case (table, reportedPhone) =>
        table.phone.map(_.asColumnOf[String]) like s"%$reportedPhone%"
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
      .filterIf(filter.companyIds.nonEmpty)(_.companyId inSetBind filter.companyIds)
      .filterIf(filter.siretSirenList.nonEmpty) { table =>
        (table.companySiret inSetBind filter.siretSirenList
          .filter(_.matches(SIRET.pattern))
          .map(SIRET.fromUnsafe)
          .distinct) ||
        (SubstrOptSQLFunction(
          table.companySiret.asColumnOf[Option[String]],
          0,
          10
        ) inSetBind filter.siretSirenList
          .filter(_.matches(SIREN.pattern))
          .distinct)
      }
      .filterOpt(filter.siretSirenDefined) { case (table, siretSirenDefined) =>
        table.companySiret.isDefined === siretSirenDefined
      }
      .filterOpt(filter.companyName) { case (table, companyName) =>
        table.companyName like s"${companyName}%"
      }
      .filterIf(filter.companyCountries.nonEmpty) { table =>
        table.companyCountry
          .map(country => country.inSet(filter.companyCountries.map(Country.fromCode)))
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
      .filterIf(filter.subcategories.nonEmpty) { table =>
        castVarCharArrayToTextArray(table.subcategories) @> filter.subcategories.toList.bind
      }
      .filterIf(filter.status.nonEmpty)(table => table.status.inSetBind(filter.status.map(_.entryName)))
      .filterIf(filter.withTags.nonEmpty) { table =>
        table.tags @& filter.withTags.toList.bind
      }
      .filterNot { table =>
        table.tags @& filter.withoutTags.toList.bind
      }
      .filterOpt(filter.details) { case (table, details) =>
        val englishQuery = table.adminSearchColumn @@ plainToTsQuery(details, Some("english"))
        val frenchQuery  = table.adminSearchColumn @@ plainToTsQuery(details, Some("french"))

        // Optimisation to avoid case and use indexes
        ((table.lang === Locale.ENGLISH) && englishQuery) || ((table.lang =!= Locale.ENGLISH) && frenchQuery)
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
      .filterOpt(filter.hasResponseEvaluation) { case (table, hasEvaluation) =>
        val exists = ResponseConsumerReviewTable.table
          .filter(x => x.reportId === table.id)
          .map(_.reportId)
          .exists
        if (hasEvaluation) exists else !exists
      }
      .filterIf(filter.responseEvaluation.nonEmpty) { table =>
        ResponseConsumerReviewTable.table
          .filter(_.reportId === table.id)
          .filter(_.evaluation.inSet(filter.responseEvaluation))
          .exists
      }
      .filterOpt(filter.hasEngagementEvaluation) { case (table, hasEngagementEvaluation) =>
        val exists = ReportEngagementReviewTable.table
          .filter(x => x.reportId === table.id)
          .map(_.reportId)
          .exists
        if (hasEngagementEvaluation) exists else !exists
      }
      .filterIf(filter.engagementEvaluation.nonEmpty) { table =>
        ReportEngagementReviewTable.table
          .filter(_.reportId === table.id)
          .filter(_.evaluation.inSet(filter.engagementEvaluation))
          .exists
      }
      .filterIf(filter.departments.nonEmpty) { table =>
        val departmentsFilter: Rep[Boolean] = filter.departments
          .flatMap(toPostalCode)
          .map(dep => table.companyPostalCode.asColumnOf[String] like s"${dep}%")
          .reduceLeft(_ || _)
        // Avoid searching departments in foreign countries
        departmentsFilter && table.companyCountry.isEmpty

      }
      .filterIf(filter.activityCodes.nonEmpty) { table =>
        table.companyActivityCode.inSetBind(filter.activityCodes)
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
        val englishQuery =
          (table.contactAgreement && table.proSearchColumn @@ plainToTsQuery(fullText, Some("english"))) ||
            table.proSearchColumnWithoutConsumer @@ plainToTsQuery(fullText)

        val frenchQuery =
          (table.contactAgreement && table.proSearchColumn @@ plainToTsQuery(fullText, Some("french"))) ||
            table.proSearchColumnWithoutConsumer @@ plainToTsQuery(fullText)

        // Optimisation to avoid case and use indexes
        ((table.lang === Locale.ENGLISH) && englishQuery) || ((table.lang =!= Locale.ENGLISH) && frenchQuery)
      }
      .joinLeft(ReportMetadataTable.table)
      .on(_.id === _.reportId)
      .filterOpt(filter.assignedUserId) { case ((_, maybeMetadataTable), assignedUserid) =>
        maybeMetadataTable.flatMap(_.assignedUserId) === assignedUserid
      }
      .joinLeft(BookmarkTable.table)
      .on { case ((report, _), bookmark) =>
        bookmark.userId === maybeUser.map(_.id) && report.id === bookmark.reportId
      }
      .joinLeft(SubcategoryLabelTable.table)
      .on { case (((report, _), _), subcategoryLabel) =>
        subcategoryLabel.category === report.category && subcategoryLabel.subcategories === report.subcategories
      }
      .map { case (((report, metadata), bookmark), subcategoryLabel) => (report, metadata, bookmark, subcategoryLabel) }
      .filterOpt(filter.isBookmarked) { case ((_, _, bookmark, _), isBookmarked) =>
        val bookmarkExists = bookmark.isDefined
        if (isBookmarked) bookmarkExists else !bookmarkExists
      }
  }

}
