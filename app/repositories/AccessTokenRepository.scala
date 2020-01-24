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

  class AccessTokenTable(tag: Tag) extends Table[AccessToken](tag, "company_access_tokens") {
    def id = column[UUID]("id", O.PrimaryKey)
    def companyId = column[UUID]("company_id")
    def token = column[String]("token")
    def level = column[AccessLevel]("level")
    def valid = column[Boolean]("valid")
    def emailedTo = column[Option[EmailAddress]]("emailed_to")
    def expirationDate = column[Option[OffsetDateTime]]("expiration_date")
    def * = (id, companyId, token, level, valid, emailedTo, expirationDate) <> (AccessToken.tupled, AccessToken.unapply)

    def company = foreignKey("COMPANY_FK", companyId, companyRepository.companyTableQuery)(_.id, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }

  val AccessTokenTableQuery = TableQuery[AccessTokenTable]

  def createToken(
      company: Company, level: AccessLevel, token: String,
      validity: Option[java.time.temporal.TemporalAmount], emailedTo: Option[EmailAddress] = None): Future[AccessToken] =
    db.run(AccessTokenTableQuery returning AccessTokenTableQuery += AccessToken(
      id = UUID.randomUUID(),
      companyId = company.id,
      token = token,
      level = level,
      valid = true,
      emailedTo = emailedTo,
      expirationDate = validity.map(OffsetDateTime.now.plus(_))
    ))

  private def fetchValidTokens(company: Company) =
    AccessTokenTableQuery
      .filter(
        _.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)
      .filter(_.companyId === company.id)

  def fetchValidToken(company: Company, emailedTo: EmailAddress): Future[Option[AccessToken]] =
    db.run(fetchValidTokens(company)
      .filter(_.emailedTo === emailedTo)
      .sortBy(_.expirationDate.desc)
      .result
      .headOption
    )

  def fetchActivationToken(company: Company): Future[Option[AccessToken]] =
    db.run(fetchValidTokens(company)
      .filterNot(_.emailedTo.isDefined)
      .filter(_.level === AccessLevel.ADMIN)
      .result
      .headOption
    )

  def getToken(company: Company, id: UUID): Future[Option[AccessToken]] =
    db.run(fetchValidTokens(company)
      .filter(_.id === id)
      .result
      .headOption
    )

  def findToken(company: Company, token: String): Future[Option[AccessToken]] =
    db.run(fetchValidTokens(company)
      .filter(_.token === token)
      .result
      .headOption
    )

  def fetchPendingTokens(company: Company): Future[List[AccessToken]] =
    db.run(fetchValidTokens(company)
      .sortBy(_.expirationDate.desc)
      .to[List]
      .result
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

  def applyToken(token: AccessToken, user: User): Future[Boolean] = {
    if (!token.valid || token.expirationDate.filter(_.isBefore(OffsetDateTime.now)).isDefined) {
      logger.debug(s"Token ${token.id} could not be applied to user ${user.id}")
      Future(false)
    } else {
      val res = db.run(DBIO.seq(
        companyRepository.upsertUserAccess(token.companyId, user.id, token.level),
        AccessTokenTableQuery.filter(_.id === token.id).map(_.valid).update(false)
      ).transactionally)
      .map(_ => true)
      logger.debug(s"Token ${token.id} applied to user ${user.id}")
      res
    }
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
            .update((level, validity.map(OffsetDateTime.now.plus(_))))
    )

  def prefetchActivationCodes(companyIds: List[UUID]): Future[Map[UUID, String]] = {
    db.run(AccessTokenTableQuery
      .filter(_.companyId inSetBind companyIds.distinct)
      .filter(_.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)
      .filterNot(_.emailedTo.isDefined)
      .to[List].result
    )
      .map(f => f.map(accessToken => accessToken.companyId -> accessToken.token).toMap)
  }

  def fetchActivationCode(company: Company): Future[Option[String]] =
    fetchActivationToken(company).map(_.map(_.token))
}
