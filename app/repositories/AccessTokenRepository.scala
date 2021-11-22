package repositories

import models._
import models.token.TokenKind
import models.token.TokenKind.CompanyInit
import models.token.TokenKind.DGCCRFAccount
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class AccessTokenRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    val companyRepository: CompanyRepository,
    val userRepository: UserRepository
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import companyRepository.AccessLevelColumnType
  import dbConfig._
  implicit val TokenKindColumnType = MappedColumnType.base[TokenKind, String](_.entryName, TokenKind.withName(_))

  class AccessTokenTable(tag: Tag) extends Table[AccessToken](tag, "access_tokens") {
    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def kind = column[TokenKind]("kind")
    def token = column[String]("token")
    def valid = column[Boolean]("valid")
    def companyId = column[Option[UUID]]("company_id")
    def level = column[Option[AccessLevel]]("level")
    def emailedTo = column[Option[EmailAddress]]("emailed_to")
    def expirationDate = column[Option[OffsetDateTime]]("expiration_date")
    def * = (
      id,
      creationDate,
      kind,
      token,
      valid,
      companyId,
      level,
      emailedTo,
      expirationDate
    ) <> (AccessToken.tupled, AccessToken.unapply)
  }

  val AccessTokenTableQuery = TableQuery[AccessTokenTable]

  def createToken(
      kind: TokenKind,
      token: String,
      validity: Option[java.time.temporal.TemporalAmount],
      companyId: Option[UUID],
      level: Option[AccessLevel],
      emailedTo: Option[EmailAddress] = None,
      creationDate: OffsetDateTime = OffsetDateTime.now
  ): Future[AccessToken] =
    db.run(
      AccessTokenTableQuery returning AccessTokenTableQuery += AccessToken(
        creationDate = creationDate,
        kind = kind,
        token = token,
        valid = true,
        companyId = companyId,
        companyLevel = level,
        emailedTo = emailedTo,
        expirationDate = validity.map(OffsetDateTime.now.plus(_))
      )
    )

  private def fetchValidTokens =
    AccessTokenTableQuery
      .filter(_.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
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

  def fetchActivationToken(companyId: UUID): Future[Option[AccessToken]] =
    db.run(
      fetchCompanyValidTokens(companyId)
        .filter(_.kind === (CompanyInit: TokenKind))
        .filter(_.level === AccessLevel.ADMIN)
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

  def findToken(company: Company, token: String): Future[Option[AccessToken]] =
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
      AccessTokenTableQuery
        .filter(_.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
        .filter(_.valid)
        .filter(_.emailedTo === emailedTo)
        .to[List]
        .result
    )

  def fetchPendingTokensDGCCRF: Future[List[AccessToken]] =
    db.run(
      AccessTokenTableQuery
        .filter(_.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
        .filter(_.valid)
        .filter(_.kind === (DGCCRFAccount: TokenKind))
        .to[List]
        .result
    )

  def applyCompanyToken(token: AccessToken, user: User): Future[Boolean] =
    if (!token.valid || token.expirationDate.exists(_.isBefore(OffsetDateTime.now))) {
      logger.debug(s"Token ${token.id} could not be applied to user ${user.id}")
      Future(false)
    } else
      db.run(
        DBIO
          .seq(
            companyRepository.createCompanyUserAccess(
              token.companyId.get,
              user.id,
              token.companyLevel.get
            ),
            AccessTokenTableQuery.filter(_.id === token.id).map(_.valid).update(false),
            AccessTokenTableQuery
              .filter(_.companyId === token.companyId)
              .filter(_.emailedTo.isEmpty)
              .map(_.valid)
              .update(false)
          )
          .transactionally
      ).map(_ => true)

  def giveCompanyAccess(company: Company, user: User, level: AccessLevel): Future[Unit] =
    db.run(
      DBIO
        .seq(
          companyRepository.createCompanyUserAccess(company.id, user.id, level),
          AccessTokenTableQuery
            .filter(_.companyId === company.id)
            .filter(_.emailedTo.isEmpty)
            .map(_.valid)
            .update(false)
        )
        .transactionally
    ).map(_ => ())

  def invalidateToken(token: AccessToken): Future[Int] =
    db.run(
      AccessTokenTableQuery
        .filter(_.id === token.id)
        .map(_.valid)
        .update(false)
    )

  def updateToken(token: AccessToken, level: AccessLevel, validity: Option[java.time.temporal.TemporalAmount]) =
    db.run(
      AccessTokenTableQuery
        .filter(_.id === token.id)
        .map(a => (a.level, a.expirationDate))
        .update((Some(level), validity.map(OffsetDateTime.now.plus(_))))
    )

  def prefetchActivationCodes(companyIds: List[UUID]): Future[Map[UUID, String]] =
    db.run(
      AccessTokenTableQuery
        .filter(_.companyId inSetBind companyIds.distinct)
        .filter(_.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
        .filter(_.valid)
        .filter(_.kind === (CompanyInit: TokenKind))
        .to[List]
        .result
    ).map(f => f.map(accessToken => accessToken.companyId.get -> accessToken.token).toMap)

  def companiesToActivate(): Future[List[(AccessToken, Company)]] =
    db.run(
      AccessTokenTableQuery
        .join(companyRepository.companyTableQuery)
        .on(_.companyId === _.id)
        .filter(_._1.creationDate < OffsetDateTime.now.withHour(0).withMinute(0).withSecond(0).withNano(0))
        .filter(_._1.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
        .filter(_._1.valid)
        .filter(_._1.kind === (CompanyInit: TokenKind))
        .to[List]
        .result
    )

  def fetchActivationCode(company: Company): Future[Option[String]] =
    fetchActivationToken(company.id).map(_.map(_.token))

  def useEmailValidationToken(token: AccessToken, user: User) =
    db.run(
      DBIO
        .seq(
          userRepository.userTableQuery
            .filter(_.id === user.id)
            .map(_.lastEmailValidation)
            .update(Some(OffsetDateTime.now)),
          AccessTokenTableQuery.filter(_.id === token.id).map(_.valid).update(false)
        )
        .transactionally
    ).map(_ => true)

  def dgccrfAccountsCurve(ticks: Int) =
    db.run(sql"""select * from (select
      my_date_trunc('month'::text, creation_date)::timestamp,
      sum(count(*)) over (order by my_date_trunc('month'::text, creation_date) rows between unbounded preceding and current row )
      from access_tokens
        where kind = 'DGCCRF_ACCOUNT' and valid = false
      group by  my_date_trunc('month'::text, creation_date)
      order by  1 DESC LIMIT #${ticks} )  as res order by 1 ASC""".as[(Timestamp, Int)])

  def dgccrfSubscription(ticks: Int): Future[Vector[(Timestamp, Int)]] =
    db.run(
      sql"""select * from ( select  my_date_trunc('month'::text,creation_date)::timestamp,
 sum(count(*)) over ( order by my_date_trunc('month'::text,creation_date)::timestamp rows between unbounded preceding and current row)
  from subscriptions s
  group by  my_date_trunc('month'::text,creation_date)
  order by  1 DESC LIMIT #${ticks} )as res order by 1 ASC""".as[(Timestamp, Int)]
    )

  def dgccrfActiveAccountsCurve(ticks: Int) =
    db.run(sql"""select * from (select my_date_trunc('month'::text, creation_date)::timestamp ,
      sum(count(*)) over (order by my_date_trunc('month'::text, creation_date) rows between 2 preceding and current row )
      from access_tokens
        where kind = 'VALIDATE_EMAIL' and valid = false
      group by  my_date_trunc('month'::text, creation_date)
      order by 1 DESC LIMIT #${ticks} ) as res order by 1 ASC""".as[(Timestamp, Int)])

  def dgccrfControlsCurve(ticks: Int) =
    db.run(
      sql"""select * from (select my_date_trunc('month'::text, creation_date)::timestamp, count(distinct company_id)
  from events
    where action = 'Contrôle effectué'
  group by  my_date_trunc('month'::text,creation_date)
  order by  1 DESC LIMIT #${ticks} ) as res order by 1 ASC""".as[(Timestamp, Int)]
    )

}
