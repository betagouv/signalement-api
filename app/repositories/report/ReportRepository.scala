package repositories.report

import com.github.tminglei.slickpg.TsVector
import models._
import models.barcode.BarcodeProduct
import models.company.Company
import models.report.ReportResponseType.ACCEPTED
import models.report.ReportStatus.SuppressionRGPD
import models.report._
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import repositories.PostgresProfile.api._
import repositories.barcode.BarcodeProductTable
import repositories.bookmark.BookmarkTable
import repositories.company.CompanyTable
import repositories.report.ReportColumnType._
import repositories.report.ReportRepository.{ReportOrdering, queryFilter}
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
import slick.ast.BaseTypedType
import slick.basic.DatabaseConfig
import slick.basic.DatabasePublisher
import slick.jdbc.{JdbcProfile, JdbcType, ResultSetConcurrency, ResultSetType}
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
    val emailAddressSplitted = report.email.split

    val similarReportQuery =
      emailAddressSplitted match {
        case Right(emailAddressSplitted) if extendedEmailComparison =>
          table
            .filter(_.category === report.category)
            .filterOpt(report.companySiret)((report, siret) =>
              SubstrSQLFunction(report.companySiret.asColumnOf[String], 0.bind, 10.bind) === SIREN
                .fromSIRET(siret)
                .value
            )
            .filterIf(report.companySiret.isEmpty)(_.companySiret.isEmpty)
            .filter(_.creationDate >= after)
            .result
            // We do this check after because we want to compare 'root' addresses for gmail (without . and +)
            .map(_.filter(report => emailAddressSplitted.isEquivalentTo(report.email.value)))
        case _ =>
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
        .filterOpt(filters.start) { case (table, start) =>
          table.creationDate >= start
        }
        .filterOpt(filters.end) { case (table, end) =>
          table.creationDate < end
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

  def countByDepartments(start: Option[OffsetDateTime], end: Option[OffsetDateTime]): Future[Seq[(String, Int)]] =
    db.run(
      table
        .filterOpt(start) { case (table, start) =>
          table.creationDate >= start
        }
        .filterOpt(end) { case (table, end) =>
          table.creationDate < end
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
          .filter { case (report, _, _, _, _, _) =>
            report.creationDate > OffsetDateTime
              .now()
              .minusMonths(ticks.toLong)
              .withDayOfMonth(1)
          }
          .groupBy { case (report, _, _, _, _, _) =>
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
        .filter { case (report, _, _, _, _, _) => report.creationDate > OffsetDateTime.now().minusWeeks(ticks.toLong) }
        .groupBy { case (report, _, _, _, _, _) =>
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
        .filter { case (report, _, _, _, _, _) => report.creationDate > OffsetDateTime.now().minusDays(11) }
        .groupBy { case (report, _, _, _, _, _) =>
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
        .map { case (tags, report) => (tags, report.size) }
        .sortBy(_._2.desc)
        .result
    ).map(spreadListOfTags)
  }

  def getHostsOfCompany(companyId: UUID): Future[Seq[(String, Int)]] =
    db.run(
      table
        .filter(_.companyId === companyId)
        .filter(_.host.isDefined)
        .groupBy(_.host)
        .map { case (host, rowsGroup) => (host, rowsGroup.size) }
        .sortBy(_._2.desc)
        .result
    ).map(_.map { case (maybeHost, nb) => (maybeHost.getOrElse(""), nb) })

  def getPhonesOfCompany(companyId: UUID): Future[Seq[(String, Int)]] =
    db.run(
      table
        .filter(_.companyId === companyId)
        .filter(_.phone.isDefined)
        .groupBy(_.phone)
        .map { case (phone, rowsGroup) => (phone, rowsGroup.size) }
        .sortBy(_._2.desc)
        .result
    ).map(_.map { case (maybePhone, nb) => (maybePhone.getOrElse(""), nb) })

  def getReportsWithFiles(
      user: Option[User],
      filter: ReportFilter
  ): Future[SortedMap[Report, List[ReportFile]]] =
    for {
      queryResult <- queryFilter(ReportTable.table(user), filter, user)
        .map { case (report, _, _, _, _, _) => report }
        .joinLeft(ReportFileTable.table)
        .on { case (report, reportFile) => report.id === reportFile.reportId }
        .withPagination(db)(maybeOffset = Some(0), maybeLimit = Some(50000))
        .sortBy { case (report, _) => report.creationDate.desc }
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
  ): Future[PaginatedResult[ReportFromSearch]] =
    queryFilter(ReportTable.table(user), filter, user)
    .withPagination(db)(offset, limit, None)
      .sortBy { t => sortReport(t._1, sortBy, orderBy) }
    .map(_.mapEntities { case (report, metadata, bookmark, consumerReview, engagementReview, subcategoryLabel) =>
      ReportFromSearch(report, metadata, bookmark, consumerReview, engagementReview, subcategoryLabel)
    })

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
  ): Future[PaginatedResult[((Option[String], Option[SIRET], Option[String]), Int)]] =
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
      .groupBy(a => (a.phone, a.companySiret, a.companyName))
      .map { case (a, b) => (a, b.length) }
      .withPagination(db)(
        maybeOffset = offset,
        maybeLimit = limit,
        maybePreliminaryAction = None
      )
      .sortBy(_._2.desc)

  override def getFor(user: Option[User], id: UUID): Future[Option[Report]] =
    db.run(
      ReportTable
        .table(user)
        .filter(_.id === id)
        .result
        .headOption
    )

  override def getForWithMetadata(user: Option[User], id: UUID): Future[Option[ReportWithMetadata]] =
    db.run(
      ReportTable
        .table(user)
        .filter(_.id === id)
        .joinLeft(SubcategoryLabelTable.table)
        .on { case (report, subcategoryLabel) =>
          report.category === subcategoryLabel.category && report.subcategories === subcategoryLabel.subcategories
        }
        .joinLeft(BookmarkTable.table)
        .on { case ((report, _), bookmark) =>
          bookmark.userId === user.map(_.id) && report.id === bookmark.reportId
        }
        .joinLeft(ReportMetadataTable.table)
        .on { case (((report, _), _), metadata) =>
          report.id === metadata.reportId
        }
        .result
        .headOption
    ).map(_.map { case (((report, subcategoryLabel), bookmark), metadata) =>
      ReportWithMetadata(report, metadata, bookmark, subcategoryLabel)
    })

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
      ).flatten.reduceLeftOption(_ || _).getOrElse(default)
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

  implicit private val localeColumnType: JdbcType[Locale] with BaseTypedType[Locale] = MappedColumnType.base[Locale, String](_.toLanguageTag, Locale.forLanguageTag)

  // N'utilise que les jointures nécessaires au filtrage
  // On pourrait ne pas faire de jointures et faire un EXISTS mais les perfs sont identiques et on remonte directement la donnée avec JOIN
  def queryFilter(
      table: Query[ReportTable, Report, Seq],
      filter: ReportFilter,
      maybeUser: Option[User]
  ) = {
    table
      .joinLeft(ReportMetadataTable.table)
      .on(_.id === _.reportId)
      .joinLeft(BookmarkTable.table)
      .on { case ((report, _), bookmark) =>
        bookmark.userId === maybeUser.map(_.id) && report.id === bookmark.reportId
      }
      .joinLeft(ResponseConsumerReviewTable.table)
      .on { case (((report, _), _), consumerReview) =>
        report.id === consumerReview.reportId
      }
      .joinLeft(ReportEngagementReviewTable.table)
      .on { case ((((report, _), _), _), engagementReview) =>
        report.id === engagementReview.reportId
      }
      .joinLeft(SubcategoryLabelTable.table)
      .on { case (((((report, _), _), _), _), subcategoryLabel) =>
        subcategoryLabel.category === report.category && subcategoryLabel.subcategories === report.subcategories
      }
      .map { case (((((report, metadata), bookmark), consumerReview), engagementReview), subcategoryLabel) =>
        (report, metadata, bookmark, consumerReview, engagementReview, subcategoryLabel)
      }
      .filterOpt(filter.email) { case ((report, _, _, _, _, _), email) =>
        report.email === EmailAddress(email)
      }
      .filterOpt(filter.consumerPhone) { case ((report, _, _, _, _, _), consumerPhone) =>
        report.consumerPhone === consumerPhone
      }
      .filterOpt(filter.hasConsumerPhone) { case ((report, _, _, _, _, _), hasConsumerPhone) =>
        report.consumerPhone.isDefined === hasConsumerPhone
      }
      .filterOpt(filter.websiteURL) { case ((report, _, _, _, _, _), websiteURL) =>
        report.websiteURL.map(_.asColumnOf[String]) like s"%$websiteURL%"
      }
      .filterOpt(filter.hasWebsite) { case ((report, _, _, _, _, _), websiteRequired) =>
        report.websiteURL.isDefined === websiteRequired
      }
      .filterOpt(filter.phone) { case ((report, _, _, _, _, _), reportedPhone) =>
        report.phone.map(_.asColumnOf[String]) like s"%$reportedPhone%"
      }
      .filterOpt(filter.hasPhone) { case ((report, _, _, _, _, _), phoneRequired) =>
        report.phone.isDefined === phoneRequired
      }
      .filterOpt(filter.hasCompany) { case ((report, _, _, _, _, _), hasCompany) =>
        report.companyId.isDefined === hasCompany
      }
      .filterOpt(filter.hasForeignCountry) { case ((report, _, _, _, _, _), hasForeignCountry) =>
        report.companyCountry.isDefined === hasForeignCountry
      }
      .filterIf(filter.companyIds.nonEmpty) { case (report, _, _, _, _, _) =>
        report.companyId inSetBind filter.companyIds
      }
      .filterIf(filter.siretSirenList.nonEmpty) { case (report, _, _, _, _, _) =>
        (report.companySiret inSetBind filter.siretSirenList
          .filter(_.matches(SIRET.pattern))
          .map(SIRET.fromUnsafe)
          .distinct) ||
          (SubstrOptSQLFunction(
            report.companySiret.asColumnOf[Option[String]],
            0,
            10
          ) inSetBind filter.siretSirenList
            .filter(_.matches(SIREN.pattern))
            .distinct)
      }
      .filterOpt(filter.companyName) { case ((report, _, _, _, _, _), companyName) =>
        report.companyName like s"${companyName}%"
      }
      .filterIf(filter.companyCountries.nonEmpty) { case (report, _, _, _, _, _) =>
        report.companyCountry.inSet(filter.companyCountries.map(Country.fromCode))
      }
      .filterOpt(filter.start) { case ((report, _, _, _, _, _), start) =>
        report.creationDate >= start
      }
      .filterOpt(filter.end) { case ((report, _, _, _, _, _), end) =>
        report.creationDate < end
      }
      .filterOpt(filter.category) { case ((report, _, _, _, _, _), category) =>
        // Condition pour récupérer les achats en sur internet soit dans la nouvelle catégorie,
        // soit dans l'ancienne catégorie à condition que le signalement ait un tag "Internet"
        if (ReportCategory.withName(category) == ReportCategory.AchatInternet) {
          report.category === category ||
            (
              report.category === ReportCategory.AchatMagasinInternet.entryName
                && report.tags @> List[ReportTag](ReportTag.Internet).bind
              )
          // Condition pour récupérer les achats en magasin soit dans la nouvelle catégorie,
          // soit dans l'ancienne catégorie à condition que le signalement n'ait pas de tag "Internet"
        } else if (ReportCategory.withName(category) == ReportCategory.AchatMagasin) {
          report.category === category ||
            (
              report.category === ReportCategory.AchatMagasinInternet.entryName
                && !(report.tags @> List[ReportTag](ReportTag.Internet).bind)
              )
        } else {
          report.category === category
        }
      }
      .filterIf(filter.subcategories.nonEmpty) { case (report, _, _, _, _, _) =>
        castVarCharArrayToTextArray(report.subcategories) @> filter.subcategories.toList.bind
      }
      .filterIf(filter.status.nonEmpty) { case (report, _, _, _, _, _) =>
        report.status.inSetBind(filter.status.map(_.entryName))
      }
      .filterIf(filter.withTags.nonEmpty) { case (report, _, _, _, _, _) =>
        report.tags @& filter.withTags.toList.bind
      }
      .filterNotIf(filter.withoutTags.nonEmpty) { case (report, _, _, _, _, _) =>
        report.tags @& filter.withoutTags.toList.bind
      }
      .filterOpt(filter.details) { case ((report, _, _, _, _, _), details) =>
        val englishQuery = report.adminSearchColumn @@ plainToTsQuery(details, Some("english"))
        val frenchQuery = report.adminSearchColumn @@ plainToTsQuery(details, Some("french"))

        // Optimisation to avoid case and use indexes
        ((report.lang === Locale.ENGLISH) && englishQuery) || ((report.lang.isEmpty || report.lang =!= Locale.ENGLISH) && frenchQuery)
      }
      // Grosse optimisation. Force Postgres à utiliser l'index de full text search.
      // Seul cas où c'est beaucoup plus rapide que d'utiliser l'index sur la creation_date
      .optimiseFullTextSearch(filter.details.isDefined)
      .filterOpt(filter.employeeConsumer) { case ((report, _, _, _, _, _), employeeConsumer) =>
        report.employeeConsumer === employeeConsumer
      }
      .filterOpt(filter.contactAgreement) { case ((report, _, _, _, _, _), contactAgreement) =>
        report.contactAgreement === contactAgreement
      }
      .filterOpt(filter.hasAttachment) { case ((report, _, _, _, _, _), hasAttachment) =>
        val exists = ReportFileTable.table
          .filter(x => x.reportId === report.id)
          .map(_.reportId)
          .exists
        if (hasAttachment) exists else !exists
      }
      .filterIf(filter.departments.nonEmpty) { case (report, _, _, _, _, _) =>
        val departmentsFilter: Rep[Boolean] = filter.departments
          .flatMap(toPostalCode)
          .map(dep => report.companyPostalCode.asColumnOf[String] like s"${dep}%")
          .reduceLeft(_ || _)
        // Avoid searching departments in foreign countries
        departmentsFilter && report.companyCountry.isEmpty

      }
      .filterIf(filter.activityCodes.nonEmpty) { case (report, _, _, _, _, _) =>
        report.companyActivityCode.inSetBind(filter.activityCodes)
      }
      .filterOpt(filter.visibleToPro) { case ((report, _, _, _, _, _), visibleToPro) =>
        report.visibleToPro === visibleToPro
      }
      .filterOpt(filter.isForeign) { case ((report, _, _, _, _, _), isForeign) =>
        if (isForeign) report.lang =!= Locale.FRENCH else report.lang === Locale.FRENCH || report.lang.isEmpty
      }
      .filterOpt(filter.hasBarcode) { case ((report, _, _, _, _, _), barcodeRequired) =>
        report.barcodeProductId.isDefined === barcodeRequired
      }
      .filterOpt(filter.fullText) { case ((report, _, _, _, _, _), fullText) =>
        val englishQuery =
          (report.contactAgreement && report.proSearchColumn @@ plainToTsQuery(fullText, Some("english"))) ||
            report.proSearchColumnWithoutConsumer @@ plainToTsQuery(fullText)

        val frenchQuery =
          (report.contactAgreement && report.proSearchColumn @@ plainToTsQuery(fullText, Some("french"))) ||
            report.proSearchColumnWithoutConsumer @@ plainToTsQuery(fullText)

        // Optimisation to avoid case and use indexes
        ((report.lang === Locale.ENGLISH) && englishQuery) || ((report.lang.isEmpty || report.lang =!= Locale.ENGLISH) && frenchQuery)
      }
      .filterOpt(filter.assignedUserId) { case (table, assignedUserid) =>
        table._2.flatMap(_.assignedUserId) === assignedUserid
      }
      .filterOpt(filter.isBookmarked) { case (table, isBookmarked) =>
        table._3.isDefined === isBookmarked
      }
      .filterOpt(filter.hasResponseEvaluation) { case (table, hasEvaluation) =>
        table._4.isDefined === hasEvaluation
      }
      .filterIf(filter.responseEvaluation.nonEmpty) { table =>
        table._4.map(_.evaluation).inSetBind(filter.responseEvaluation)
      }
      .filterOpt(filter.hasEngagementEvaluation) { case (table, hasEngagementEvaluation) =>
        table._5.isDefined === hasEngagementEvaluation
      }
      .filterIf(filter.engagementEvaluation.nonEmpty) { table =>
        table._5.map(_.evaluation).inSetBind(filter.engagementEvaluation)
      }
  }
}