package repositories.company

import models.company.SearchCompanyIdentity.SearchCompanyIdentityId
import models.company.SearchCompanyIdentity.SearchCompanyIdentityName
import models.company.SearchCompanyIdentity.SearchCompanyIdentityRCS
import models.company.SearchCompanyIdentity.SearchCompanyIdentitySiren
import models.company.SearchCompanyIdentity.SearchCompanyIdentitySiret
import models._
import models.company.Address
import models.company.Company
import models.company.CompanyRegisteredSearch
import models.report.ReportStatus.statusWithProResponse
import repositories.PostgresProfile.api._
import repositories.companyaccess.CompanyAccessTable
import repositories.report.ReportTable
import repositories.user.UserTable
import slick.jdbc.JdbcProfile
import utils.Country
import utils.EmailAddress
import utils.SIREN
import utils.SIRET
import repositories.CRUDRepository
import slick.basic.DatabaseConfig
import slick.lifted.Rep
import utils.Constants.ActionEvent.POST_FOLLOW_UP_DOC
import utils.Constants.ActionEvent.REPORT_CLOSED_BY_NO_READING
import utils.Constants.Departments.toPostalCode

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[CompanyTable, Company]
    with CompanyRepositoryInterface {

  override val table: TableQuery[CompanyTable] = CompanyTable.table
  import dbConfig._

  private def least(elements: Rep[Option[Double]]*): Rep[Option[Double]] =
    SimpleFunction[Option[Double]]("least").apply(elements)

  override def searchWithReportsCount(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch,
      userRole: UserRole
  ): Future[PaginatedResult[(Company, Int, Int)]] = {
    def companyIdByEmailTable(emailWithAccess: EmailAddress) = CompanyAccessTable.table
      .join(UserTable.table)
      .on(_.userId === _.id)
      .filter(_._2.email === emailWithAccess)
      .map(_._1.companyId)

    val setThreshold: DBIO[Int] = sqlu"""SET pg_trgm.similarity_threshold = 0.32"""

    val query = table
      .joinLeft(ReportTable.table(Some(userRole)))
      .on(_.id === _.companyId)
      .filterIf(search.departments.nonEmpty) { case (company, report) =>
        val departmentsFilter: Rep[Boolean] = search.departments
          .flatMap(toPostalCode)
          .map { dep =>
            company.postalCode.asColumnOf[String] like s"${dep}%"
          }
          .reduceLeft(_ || _)
        // Avoid searching departments in foreign countries
        departmentsFilter && report.flatMap(_.companyCountry).isEmpty
      }
      .filterIf(search.activityCodes.nonEmpty) { case (company, _) =>
        company.activityCode.map(a => a.inSet(search.activityCodes)).getOrElse(false)
      }
      .groupBy(_._1)
      .map { case (grouped, all) =>
        (
          grouped,
          all.map(_._2).map(_.map(_.id)).countDefined,
          /* Response rate
           * Equivalent to following select clause
           * count((case when (status in ('Promesse action','Signalement infondé','Signalement mal attribué') then id end))
           */
          all
            .map(_._2)
            .map(b =>
              b.flatMap { a =>
                Case If a.status.inSet(
                  statusWithProResponse.map(_.entryName)
                ) Then a.id
              }
            )
            .countDefined: Rep[Int]
        )
      }
      .sortBy(_._2.desc)

    val maybePreliminaryAction = search.identity.flatMap {
      case SearchCompanyIdentityName(_) => Some(setThreshold)
      case _                            => None
    }

    search.identity
      .map {
        case SearchCompanyIdentityRCS(q)   => query.filter(_._1.id.asColumnOf[String] like s"%${q}%")
        case SearchCompanyIdentitySiret(q) => query.filter(_._1.siret === SIRET.fromUnsafe(q))
        case SearchCompanyIdentitySiren(q) => query.filter(_._1.siret.asColumnOf[String] like s"${q}_____")
        case SearchCompanyIdentityName(q) =>
          query
            .filter(tuple =>
              tuple._1.name.?                        % q ||
                tuple._1.brand                       % q ||
                tuple._1.commercialName              % q ||
                tuple._1.establishmentCommercialName % q
            )
            .sortBy(tuple =>
              least(
                tuple._1.name.? <-> q,
                tuple._1.brand <-> q,
                tuple._1.commercialName <-> q,
                tuple._1.establishmentCommercialName <-> q
              )
            )
        case id: SearchCompanyIdentityId => query.filter(_._1.id === id.value)
      }
      .getOrElse(query)
      .filterOpt(search.emailsWithAccess) { case (table, email) =>
        table._1.id.in(companyIdByEmailTable(EmailAddress(email)))
      }
      .withPagination(db)(
        maybeOffset = paginate.offset,
        maybeLimit = paginate.limit,
        maybePreliminaryAction = maybePreliminaryAction
      )

  }

  override def getOrCreate(siret: SIRET, data: Company): Future[Company] =
    db.run(table.filter(_.siret === siret).result.headOption)
      .flatMap(
        _.map(Future(_)).getOrElse(db.run(table returning table += data))
      )

  override def fetchCompanies(companyIds: List[UUID]): Future[List[Company]] =
    db.run(table.filter(_.id inSetBind companyIds).to[List].result)

  override def findBySiret(siret: SIRET): Future[Option[Company]] =
    db.run(table.filter(_.siret === siret).result.headOption)

  def findCompanyAndHeadOffice(siret: SIRET): Future[List[Company]] =
    db.run(
      table
        .filter(_.siret.asColumnOf[String] like s"${SIREN.fromSIRET(siret).value}%")
        .filter { companyTable =>
          val companyWithSameSiret: Rep[Boolean] = companyTable.siret === siret
          val companyHeadOffice: Rep[Boolean]    = companyTable.isHeadOffice
          companyWithSameSiret || companyHeadOffice
        }
        .filter(_.isOpen)
        .result
        .map(_.toList)
    )

  def findHeadOffices(siren: List[SIREN], openOnly: Boolean): Future[List[Company]] =
    db.run(
      table
        .filter(x => SubstrSQLFunction(x.siret.asColumnOf[String], 0.bind, 10.bind) inSetBind siren.map(_.value))
        .filterIf(openOnly) { case (table) => table.isOpen }
        .filter(_.isHeadOffice)
        .result
        .map(_.toList)
    )

  override def findBySirets(sirets: List[SIRET]): Future[List[Company]] =
    db.run(table.filter(_.siret inSet sirets).to[List].result)

  override def findByName(name: String): Future[List[Company]] =
    db.run(table.filter(_.name.toLowerCase like s"%${name.toLowerCase}%").to[List].result)

  override def findBySiren(siren: List[SIREN]): Future[List[Company]] =
    db.run(
      table
        .filter(x => SubstrSQLFunction(x.siret.asColumnOf[String], 0.bind, 10.bind) inSetBind siren.map(_.value))
        .to[List]
        .result
    )

  override def updateBySiret(
      siret: SIRET,
      isOpen: Boolean,
      isHeadOffice: Boolean,
      isPublic: Boolean,
      number: Option[String],
      street: Option[String],
      addressSupplement: Option[String],
      name: String,
      brand: Option[String],
      country: Option[Country]
  ): Future[SIRET] =
    db
      .run(
        table
          .filter(_.siret === siret)
          .map(c =>
            (
              c.isHeadOffice,
              c.isOpen,
              c.isPublic,
              c.streetNumber,
              c.street,
              c.addressSupplement,
              c.name,
              c.brand,
              c.country
            )
          )
          .update((isHeadOffice, isOpen, isPublic, number, street, addressSupplement, name, brand, country))
      )
      .map(_ => siret)

  override def getInactiveCompanies: Future[List[(Company, Int)]] = {
    val query = sql"""
       WITH ignored_reports_on_period AS (
             SELECT
                 reports.company_id,
                 COUNT(reports.id) AS count_ignored
             FROM reports
                  INNER JOIN events ON reports.id = events.report_id
                  WHERE events.action = ${REPORT_CLOSED_BY_NO_READING.value}
                    AND reports.creation_date >= NOW() - INTERVAL '3 months'
             GROUP BY
                 reports.company_id
       ),
           count_reports_on_period AS (
                   SELECT
                       company_id,
                       COUNT(reports.id) AS nb_reports
                   FROM
                       reports
                   WHERE
                           reports.creation_date >= NOW() - INTERVAL '3 months'
                   GROUP BY
                       company_id
               ),
            count_follow_up_on_period AS (
                    SELECT
                        company_id,
                        COALESCE(COUNT(events.id), 0) AS nb_follow_up
                    FROM
                        events
                    WHERE
                      events.creation_date >= NOW() - INTERVAL '3 months'
                      AND events.action = ${POST_FOLLOW_UP_DOC.value}
                    GROUP BY
                        company_id
                )
       SELECT
          c.id, c.siret, c.creation_date, c.name, c.activity_code,
          c.street_number, c.street, c.address_supplement, c.city, c.postal_code,
          c.is_headoffice, c.is_open, c.is_public, c.brand, c.commercial_name, c.establishment_commercial_name, c.country,
          COALESCE(ir.count_ignored, 0) AS count_ignored
       FROM
          companies c
          LEFT JOIN ignored_reports_on_period ir ON c.id = ir.company_id
          LEFT JOIN count_reports_on_period ar ON c.id = ar.company_id
          LEFT OUTER JOIN count_follow_up_on_period cf ON c.id = cf.company_id
       WHERE
          c.country is null
          AND EXISTS (
             SELECT 1
             FROM
                company_accesses ca
                INNER JOIN users u ON ca.user_id = u.id
                INNER JOIN auth_attempts aa ON u.email = aa.login
             WHERE
                c.id = ca.company_id
                AND aa.is_success = true
          )
          AND
          COALESCE(ir.count_ignored, 0) = COALESCE(ar.nb_reports, 0)
          AND COALESCE(cf.nb_follow_up, 0) = 0
          AND c.is_open = true
          AND COALESCE(ar.nb_reports, 0) > 0;
    """.as[
      (
          String,
          String,
          Timestamp,
          String,
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Boolean,
          Boolean,
          Boolean,
          Option[String],
          Option[String],
          Option[String],
          Option[String],
          Int
      )
    ]

    db.run(query).map { rows =>
      rows.map {
        case (
              id,
              siret,
              creationDate,
              name,
              activityCode,
              streetNumber,
              street,
              addressSupplement,
              city,
              postalCode,
              isHeadOffice,
              isOpen,
              isPublic,
              brand,
              commercialName,
              establishmentCommercialName,
              country,
              countIgnored
            ) =>
          val company = Company(
            id = UUID.fromString(id),
            siret = SIRET.fromUnsafe(siret),
            creationDate = OffsetDateTime.ofInstant(creationDate.toInstant, ZoneOffset.UTC),
            name = name,
            address = Address(
              number = streetNumber,
              street = street,
              addressSupplement = addressSupplement,
              postalCode = postalCode,
              city = city,
              country = country.map(Country.fromCode)
            ),
            activityCode = activityCode,
            isHeadOffice = isHeadOffice,
            isOpen = isOpen,
            isPublic = isPublic,
            brand = brand,
            commercialName = commercialName,
            establishmentCommercialName = establishmentCommercialName
          )
          (company, countIgnored)
      }.toList
    }
  }

}
