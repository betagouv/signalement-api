package repositories.accesstoken

import models._
import models.token.TokenKind
import models.token.TokenKind.CompanyInit
import models.token.TokenKind.DGCCRFAccount
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import repositories.accesstoken.AccessTokenColumnType._
import repositories.company.CompanyTable
import repositories.companyaccess.CompanyAccessColumnType._
import repositories.user.UserRepository
import repositories.user.UserTable
import repositories.PostgresProfile
import repositories.companyaccess.CompanyAccessRepository
import repositories.computeTickValues
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AccessTokenRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    val companyAccessRepository: CompanyAccessRepository,
    val userRepository: UserRepository
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  def createToken(
      kind: TokenKind,
      token: String,
      validity: Option[java.time.temporal.TemporalAmount],
      companyId: Option[UUID],
      level: Option[AccessLevel],
      emailedTo: Option[EmailAddress] = None,
      creationDate: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
  ): Future[AccessToken] =
    db.run(
      AccessTokenTable.table returning AccessTokenTable.table += AccessToken(
        creationDate = creationDate,
        kind = kind,
        token = token,
        valid = true,
        companyId = companyId,
        companyLevel = level,
        emailedTo = emailedTo,
        expirationDate = validity.map(OffsetDateTime.now(ZoneOffset.UTC).plus(_))
      )
    )

  private def fetchValidTokens =
    AccessTokenTable.table
      .filter(_.expirationDate.filter(_ < OffsetDateTime.now(ZoneOffset.UTC)).isEmpty)
      .filter(_.valid)

  private def fetchCompanyValidTokens(companyId: UUID): Query[AccessTokenTable, AccessToken, Seq] =
    fetchValidTokens.filter(_.companyId === companyId)

  private def fetchCompanyValidTokens(company: Company): Query[AccessTokenTable, AccessToken, Seq] =
    fetchCompanyValidTokens(company.id)

  def fetchToken(company: Company, emailedTo: EmailAddress): Future[Option[AccessToken]] =
    db.run(
      fetchCompanyValidTokens(company)
        .filter(_.emailedTo === emailedTo)
        .sortBy(_.expirationDate.desc)
        .result
        .headOption
    )

  def fetchValidActivationToken(companyId: UUID): Future[Option[AccessToken]] =
    db.run(
      fetchCompanyValidTokens(companyId)
        .filter(_.kind === (CompanyInit: TokenKind))
        .filter(_.level === AccessLevel.ADMIN)
        .result
        .headOption
    )

  def fetchActivationToken(companyId: UUID): Future[Option[AccessToken]] =
    db.run(
      AccessTokenTable.table
        .filter(_.companyId === companyId)
        .filter(_.kind === (CompanyInit: TokenKind))
        .filter(_.level === AccessLevel.ADMIN)
        .result
        .headOption
    )

  def get(tokenId: UUID): Future[Option[AccessToken]] =
    db.run(
      AccessTokenTable.table
        .filter(_.id === tokenId)
        .result
        .headOption
    )

  def getToken(company: Company, id: UUID): Future[Option[AccessToken]] =
    db.run(
      fetchCompanyValidTokens(company)
        .filter(_.id === id)
        .result
        .headOption
    )

  def findToken(token: String): Future[Option[AccessToken]] =
    db.run(
      fetchValidTokens
        .filter(_.token === token)
        .filterNot(_.companyId.isDefined)
        .result
        .headOption
    )

  def findValidToken(company: Company, token: String): Future[Option[AccessToken]] =
    db.run(
      fetchCompanyValidTokens(company)
        .filter(_.token === token)
        .result
        .headOption
    )

  def fetchPendingTokens(company: Company): Future[List[AccessToken]] =
    db.run(
      fetchCompanyValidTokens(company)
        .sortBy(_.expirationDate.desc)
        .to[List]
        .result
    )

  def removePendingTokens(company: Company): Future[Int] = db.run(
    fetchCompanyValidTokens(company).delete
  )

  def fetchPendingTokens(emailedTo: EmailAddress): Future[List[AccessToken]] =
    db.run(
      AccessTokenTable.table
        .filter(_.expirationDate.filter(_ < OffsetDateTime.now(ZoneOffset.UTC)).isEmpty)
        .filter(_.valid)
        .filter(_.emailedTo === emailedTo)
        .to[List]
        .result
    )

  def fetchPendingTokensDGCCRF: Future[List[AccessToken]] =
    db.run(
      AccessTokenTable.table
        .filter(_.expirationDate.filter(_ < OffsetDateTime.now(ZoneOffset.UTC)).isEmpty)
        .filter(_.valid)
        .filter(_.kind === (DGCCRFAccount: TokenKind))
        .to[List]
        .result
    )

  // TODO move to orchestrator...
  def createCompanyAccessAndRevokeToken(token: AccessToken, user: User): Future[Boolean] =
    db.run(
      DBIO
        .seq(
          companyAccessRepository.createCompanyUserAccess(
            token.companyId.get,
            user.id,
            token.companyLevel.get
          ),
          AccessTokenTable.table.filter(_.id === token.id).map(_.valid).update(false),
          AccessTokenTable.table
            .filter(_.companyId === token.companyId)
            .filter(_.emailedTo.isEmpty)
            .map(_.valid)
            .update(false)
        )
        .transactionally
    ).map(_ => true)

  // TODO move to orchestrator...
  def giveCompanyAccess(company: Company, user: User, level: AccessLevel): Future[Unit] =
    db.run(
      DBIO
        .seq(
          companyAccessRepository.createCompanyUserAccess(company.id, user.id, level),
          AccessTokenTable.table
            .filter(_.companyId === company.id)
            .filter(_.emailedTo.isEmpty)
            .map(_.valid)
            .update(false)
        )
        .transactionally
    ).map(_ => ())

  def invalidateToken(token: AccessToken): Future[Int] =
    db.run(
      AccessTokenTable.table
        .filter(_.id === token.id)
        .map(_.valid)
        .update(false)
    )

  def updateToken(token: AccessToken, level: AccessLevel, validity: Option[java.time.temporal.TemporalAmount]) =
    db.run(
      AccessTokenTable.table
        .filter(_.id === token.id)
        .map(a => (a.level, a.expirationDate))
        .update((Some(level), validity.map(OffsetDateTime.now(ZoneOffset.UTC).plus(_))))
    )

  def prefetchActivationCodes(companyIds: List[UUID]): Future[Map[UUID, String]] =
    db.run(
      AccessTokenTable.table
        .filter(_.companyId inSetBind companyIds.distinct)
        .filter(_.expirationDate.filter(_ < OffsetDateTime.now(ZoneOffset.UTC)).isEmpty)
        .filter(_.valid)
        .filter(_.kind === (CompanyInit: TokenKind))
        .to[List]
        .result
    ).map(f => f.map(accessToken => accessToken.companyId.get -> accessToken.token).toMap)

  def companiesToActivate(): Future[List[(AccessToken, Company)]] =
    db.run(
      AccessTokenTable.table
        .join(CompanyTable.table)
        .on(_.companyId === _.id)
        .filter(
          _._1.creationDate < OffsetDateTime.now(ZoneOffset.UTC).withHour(0).withMinute(0).withSecond(0).withNano(0)
        )
        .filter(_._1.expirationDate.filter(_ < OffsetDateTime.now(ZoneOffset.UTC)).isEmpty)
        .filter(_._1.valid)
        .filter(_._1.kind === (CompanyInit: TokenKind))
        .to[List]
        .result
    )

  def fetchActivationCode(company: Company): Future[Option[String]] =
    fetchValidActivationToken(company.id).map(_.map(_.token))

  def useEmailValidationToken(token: AccessToken, user: User) =
    db.run(
      DBIO
        .seq(
          UserTable.table
            .filter(_.id === user.id)
            .map(_.lastEmailValidation)
            .update(Some(OffsetDateTime.now(ZoneOffset.UTC))),
          AccessTokenTable.table.filter(_.id === token.id).map(_.valid).update(false)
        )
        .transactionally
    ).map(_ => true)

  def dgccrfAccountsCurve(ticks: Int) =
    db.run(sql"""
      select *
      from (
        select my_date_trunc('month'::text, creation_date)::timestamp,
           sum(count(*)) over ( order by my_date_trunc('month'::text, creation_date)::timestamp rows between unbounded preceding and current row)
        from (
          select MAX(creation_date) as creation_date from access_tokens
          where kind = 'DGCCRF_ACCOUNT'
           and valid = false
           and emailed_to in (select email from users where role = 'DGCCRF')
            group by emailed_to
        ) as a
        group by my_date_trunc('month'::text, creation_date)
        order by 1 DESC LIMIT #${ticks}
      ) as res
      order by 1 ASC;
    """.as[(Timestamp, Int)])

  def dgccrfSubscription(ticks: Int): Future[Vector[(Timestamp, Int)]] =
    db.run(sql"""select * from (select v.a, count(distinct s.user_id) from subscriptions s right join
                                               (SELECT a
                                                FROM (VALUES #${computeTickValues(ticks)} ) AS X(a))
                                                   as v on creation_date <= (my_date_trunc('month'::text, v.a)::timestamp + '1 month'::interval - '1 day'::interval)

group by v.a ) as res order by 1 ASC""".as[(Timestamp, Int)])

  def dgccrfActiveAccountsCurve(ticks: Int) =
    db.run(sql"""select * from (select v.a, count(distinct ac.emailed_to) from access_tokens ac right join
                                               (SELECT a
                                                FROM (VALUES #${computeTickValues(ticks)} ) AS X(a))
                                                   as v on creation_date between (my_date_trunc('month'::text, v.a)::timestamp + '1 month'::interval - '1 day'::interval) - '4 month'::interval 
                                                         and (my_date_trunc('month'::text, v.a)::timestamp + '1 month'::interval - '1 day'::interval) and kind = 'VALIDATE_EMAIL' and valid = false

group by v.a ) as res order by 1 ASC""".as[(Timestamp, Int)])

  def dgccrfControlsCurve(ticks: Int) =
    db.run(
      sql"""select * from (select my_date_trunc('month'::text, creation_date)::timestamp, count(distinct company_id)
  from events
    where action = 'Contrôle effectué'
  group by  my_date_trunc('month'::text,creation_date)
  order by  1 DESC LIMIT #${ticks} ) as res order by 1 ASC""".as[(Timestamp, Int)]
    )

}
