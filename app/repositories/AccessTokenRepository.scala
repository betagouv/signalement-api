package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import play.api.Logger
import slick.jdbc.JdbcProfile

import models._
import utils.EmailAddress

@Singleton
class AccessTokenRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     val companyRepository: CompanyRepository, val userRepository: UserRepository)
                                     (implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass())
  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._
  import companyRepository.AccessLevelColumnType
  implicit val TokenKindColumnType = MappedColumnType.base[TokenKind, String](_.value, TokenKind.fromValue(_))

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
    def * = (id, creationDate, kind, token, valid, companyId, level, emailedTo, expirationDate) <> (AccessToken.tupled, AccessToken.unapply)
  }

  val AccessTokenTableQuery = TableQuery[AccessTokenTable]

  def createToken(
      kind: TokenKind, token: String, validity: Option[java.time.temporal.TemporalAmount],
      company: Option[Company], level: Option[AccessLevel], emailedTo: Option[EmailAddress] = None, creationDate: OffsetDateTime = OffsetDateTime.now
    ): Future[AccessToken] =
    db.run(AccessTokenTableQuery returning AccessTokenTableQuery += AccessToken(
      id = UUID.randomUUID(),
      creationDate = creationDate,
      kind = kind,
      token = token,
      valid = true,
      companyId = company.map(_.id),
      companyLevel = level,
      emailedTo = emailedTo,
      expirationDate = validity.map(OffsetDateTime.now.plus(_))
    ))

  private def fetchValidTokens =
    AccessTokenTableQuery
      .filter(
        _.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)

  private def fetchCompanyValidTokens(company: Company) =
    fetchValidTokens.filter(_.companyId === company.id)

  def fetchToken(company: Company, emailedTo: EmailAddress): Future[Option[AccessToken]] =
    db.run(fetchCompanyValidTokens(company)
      .filter(_.emailedTo === emailedTo)
      .sortBy(_.expirationDate.desc)
      .result
      .headOption
    )

  def fetchActivationToken(company: Company): Future[Option[AccessToken]] =
    db.run(fetchCompanyValidTokens(company)
      .filter(_.kind === TokenKind.COMPANY_INIT)
      .filter(_.level === AccessLevel.ADMIN)
      .result
      .headOption
    )

  def getToken(company: Company, id: UUID): Future[Option[AccessToken]] =
    db.run(fetchCompanyValidTokens(company)
      .filter(_.id === id)
      .result
      .headOption
    )

  def findToken(token: String): Future[Option[AccessToken]] =
    db.run(fetchValidTokens
      .filter(_.token === token)
      .filterNot(_.companyId.isDefined)
      .result
      .headOption
    )

  def findToken(company: Company, token: String): Future[Option[AccessToken]] =
    db.run(fetchCompanyValidTokens(company)
      .filter(_.token === token)
      .result
      .headOption
    )

  def fetchPendingTokens(company: Company): Future[List[AccessToken]] =
    db.run(fetchCompanyValidTokens(company)
      .sortBy(_.expirationDate.desc)
      .to[List]
      .result
    )

  def removePendingTokens(company: Company): Future[Int] = db.run(
    fetchCompanyValidTokens(company).delete
  )

  def fetchPendingTokens(emailedTo: EmailAddress): Future[List[AccessToken]] =
    db.run(AccessTokenTableQuery
      .filter(
        _.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)
      .filter(_.emailedTo === emailedTo)
      .to[List]
      .result
    )

  def fetchPendingTokensDGCCRF: Future[List[AccessToken]] =
    db.run(AccessTokenTableQuery
      .filter(
        _.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)
      .filter(_.kind === TokenKind.DGCCRF_ACCOUNT)
      .to[List]
      .result
    )

  def applyCompanyToken(token: AccessToken, user: User): Future[Boolean] = {
    if (!token.valid || token.expirationDate.filter(_.isBefore(OffsetDateTime.now)).isDefined) {
      logger.debug(s"Token ${token.id} could not be applied to user ${user.id}")
      Future(false)
    } else db.run(DBIO.seq(
        companyRepository.upsertUserAccess(token.companyId.get, user.id, token.companyLevel.get),
        AccessTokenTableQuery.filter(_.id === token.id).map(_.valid).update(false),
        AccessTokenTableQuery.filter(_.companyId === token.companyId).filter(_.emailedTo.isEmpty).map(_.valid).update(false)
      ).transactionally)
      .map(_ => true)
  }

  def giveCompanyAccess(company: Company, user: User, level: AccessLevel): Future[Unit] = {
    db.run(DBIO.seq(
      companyRepository.upsertUserAccess(company.id, user.id, level),
      AccessTokenTableQuery.filter(_.companyId === company.id).filter(_.emailedTo.isEmpty).map(_.valid).update(false)
    ).transactionally)
    .map(_ => Unit)
  }

  def invalidateToken(token: AccessToken): Future[Int] =
    db.run(AccessTokenTableQuery
            .filter(_.id === token.id)
            .map(_.valid)
            .update(false)
    )

  def updateToken(token: AccessToken, level: AccessLevel, validity: Option[java.time.temporal.TemporalAmount]) =
    db.run(AccessTokenTableQuery
            .filter(_.id === token.id)
            .map(a => (a.level, a.expirationDate))
            .update((Some(level), validity.map(OffsetDateTime.now.plus(_))))
    )

  def prefetchActivationCodes(companyIds: List[UUID]): Future[Map[UUID, String]] = {
    db.run(AccessTokenTableQuery
      .filter(_.companyId inSetBind companyIds.distinct)
      .filter(_.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)
      .filter(_.kind === TokenKind.COMPANY_INIT)
      .to[List].result
    )
      .map(f => f.map(accessToken => accessToken.companyId.get -> accessToken.token).toMap)
  }

  def companiesToActivate(): Future[List[(AccessToken, Company)]] =
    db.run(AccessTokenTableQuery
      .join(companyRepository.companyTableQuery).on(_.companyId === _.id)
      .filter(_._1.creationDate < OffsetDateTime.now.withHour(0).withMinute(0).withSecond(0).withNano(0))
      .filter(_._1.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_._1.valid)
      .filter(_._1.kind === TokenKind.COMPANY_INIT)
      .to[List].result
    )

  def fetchActivationCode(company: Company): Future[Option[String]] =
    fetchActivationToken(company).map(_.map(_.token))

  def useEmailValidationToken(token: AccessToken, user: User) =
    db.run(DBIO.seq(
      userRepository.userTableQuery.filter(_.id === user.id).map(_.lastEmailValidation).update(Some(OffsetDateTime.now)),
      AccessTokenTableQuery.filter(_.id === token.id).map(_.valid).update(false)
    ).transactionally)
    .map(_ => true)
}
