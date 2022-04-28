package repositories.company

import models._
import models.report.ReportStatus.ReportStatusProResponse
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import repositories.companyaccess.CompanyAccessTable
import repositories.report.ReportTable
import repositories.user.UserTable
import slick.jdbc.JdbcProfile
import utils.EmailAddress
import utils.SIREN
import utils.SIRET

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  def searchWithReportsCount(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch,
      userRole: UserRole
  ): Future[PaginatedResult[(Company, Int, Int)]] = {
    def companyIdByEmailTable(emailWithAccess: EmailAddress) = CompanyAccessTable.table
      .join(UserTable.table)
      .on(_.userId === _.id)
      .filter(_._2.email === emailWithAccess)
      .map(_._1.companyId)

    val query = CompanyTable.table
      .joinLeft(ReportTable.table(userRole))
      .on(_.id === _.companyId)
      .filterIf(search.departments.nonEmpty) { case (company, _) =>
        company.department.map(a => a.inSet(search.departments)).getOrElse(false)
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
          (
            all
              .map(_._2)
              .map(b =>
                b.flatMap { a =>
                  Case If a.status.inSet(
                    ReportStatusProResponse.map(_.entryName)
                  ) Then a.id
                }
              )
            )
            .countDefined: Rep[Int]
        )
      }
      .sortBy(_._2.desc)
    val filterQuery = search.identity
      .map {
        case SearchCompanyIdentityRCS(q)   => query.filter(_._1.id.asColumnOf[String] like s"%${q}%")
        case SearchCompanyIdentitySiret(q) => query.filter(_._1.siret === SIRET.fromUnsafe(q))
        case SearchCompanyIdentitySiren(q) => query.filter(_._1.siret.asColumnOf[String] like s"${q}_____")
        case SearchCompanyIdentityName(q)  => query.filter(_._1.name.toLowerCase like s"%${q.toLowerCase}%")
        case id: SearchCompanyIdentityId   => query.filter(_._1.id === id.value)
      }
      .getOrElse(query)
      .filterOpt(search.emailsWithAccess) { case (table, email) =>
        table._1.id.in(companyIdByEmailTable(EmailAddress(email)))
      }

    toPaginate(filterQuery, paginate.offset, paginate.limit)
  }

  def toPaginate[A, B](
      query: slick.lifted.Query[A, B, Seq],
      offsetOpt: Option[Long],
      limitOpt: Option[Int]
  ): Future[PaginatedResult[B]] = {
    val offset = offsetOpt.getOrElse(0L)
    val limit = limitOpt.getOrElse(10)
    val resultF = db.run(query.drop(offset).take(limit).result)
    val countF = db.run(query.length.result)
    for {
      result <- resultF
      count <- countF
    } yield PaginatedResult(
      totalCount = count,
      entities = result.toList,
      hasNextPage = count - (offset + limit) > 0
    )
  }

  def getOrCreate(siret: SIRET, data: Company): Future[Company] =
    db.run(CompanyTable.table.filter(_.siret === siret).result.headOption)
      .flatMap(
        _.map(Future(_)).getOrElse(db.run(CompanyTable.table returning CompanyTable.table += data))
      )

  def update(company: Company): Future[Company] = {
    val queryCompany =
      for (refCompany <- CompanyTable.table if refCompany.id === company.id)
        yield refCompany
    db.run(queryCompany.update(company))
      .map(_ => company)
  }

  def fetchCompany(id: UUID) =
    db.run(CompanyTable.table.filter(_.id === id).result.headOption)

  def fetchCompanies(companyIds: List[UUID]): Future[List[Company]] =
    db.run(CompanyTable.table.filter(_.id inSetBind companyIds).to[List].result)

  def findBySiret(siret: SIRET): Future[Option[Company]] =
    db.run(CompanyTable.table.filter(_.siret === siret).result.headOption)

  def findBySirets(sirets: List[SIRET]): Future[List[Company]] =
    db.run(CompanyTable.table.filter(_.siret inSet sirets).to[List].result)

  def findByName(name: String): Future[List[Company]] =
    db.run(CompanyTable.table.filter(_.name.toLowerCase like s"%${name.toLowerCase}%").to[List].result)

  def findBySiren(siren: List[SIREN]): Future[List[Company]] =
    db.run(
      CompanyTable.table
        .filter(x => SubstrSQLFunction(x.siret.asColumnOf[String], 0.bind, 10.bind) inSetBind siren.map(_.value))
        .to[List]
        .result
    )

}
