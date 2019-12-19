package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import models._
import utils.EmailAddress

@Singleton
class CompanyAccessRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     val companyRepository: CompanyRepository, val userRepository: UserRepository)
                                     (implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel.fromValue(_))

  class UserAccessTable(tag: Tag) extends Table[UserAccess](tag, "company_accesses") {
    def companyId = column[UUID]("company_id")
    def userId = column[UUID]("user_id")
    def level = column[AccessLevel]("level")
    def updateDate = column[OffsetDateTime]("update_date")
    def pk = primaryKey("pk_company_user", (companyId, userId))
    def * = (companyId, userId, level, updateDate) <> (UserAccess.tupled, UserAccess.unapply)

    def company = foreignKey("COMPANY_FK", companyId, companyRepository.companyTableQuery)(_.id, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
    def user = foreignKey("USER_FK", userId, userRepository.userTableQuery)(_.id, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }

  val UserAccessTableQuery = TableQuery[UserAccessTable]

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

  def getUserLevel(companyId: UUID, user: User): Future[AccessLevel] =
    db.run(UserAccessTableQuery
      .filter(_.companyId === companyId)
      .filter(_.userId === user.id)
      .map(_.level)
      .result
      .headOption
    ).map(_.getOrElse(AccessLevel.NONE))

  def fetchCompaniesWithLevel(user: User): Future[List[(Company, AccessLevel)]] =
    db.run(UserAccessTableQuery.join(companyRepository.companyTableQuery).on(_.companyId === _.id)
      .filter(_._1.userId === user.id)
      .filter(_._1.level =!= AccessLevel.NONE)
      .sortBy(_._1.updateDate.desc)
      .map(r => (r._2, r._1.level))
      .to[List]
      .result
    )

  def fetchUsersWithLevel(company: Company): Future[List[(User, AccessLevel)]] =
    db.run(UserAccessTableQuery.join(userRepository.userTableQuery).on(_.userId === _.id)
      .filter(_._1.companyId === company.id)
      .filter(_._1.level =!= AccessLevel.NONE)
      .sortBy(entry => (entry._1.level, entry._2.email))
      .map(r => (r._2, r._1.level))
      .to[List]
      .result
    )

  def fetchAdminsByCompany(companyIds: Seq[UUID]): Future[Map[UUID, List[User]]] = {
    db.run(
      (for {
        access    <- UserAccessTableQuery           if access.level === AccessLevel.ADMIN && (access.companyId inSetBind companyIds)
        user      <- userRepository.userTableQuery  if user.id === access.userId
      } yield (access.companyId, user))
      .to[List]
      .result
    ).map(_.groupBy(_._1).mapValues(_.map(_._2)))
  }

  def fetchAdmins(company: Company): Future[List[User]] =
    db.run(UserAccessTableQuery.join(userRepository.userTableQuery).on(_.userId === _.id)
      .filter(_._1.companyId === company.id)
      .filter(_._1.level === AccessLevel.ADMIN)
      .map(_._2)
      .to[List]
      .result
    )
  
  // The method below provides access to the user's unique Company
  // until we have a way to handle multiple companies per user
  // There should always be 1 company per pro user 
  def findUniqueCompany(user: User): Future[Company] =
    for {
      accesses <- fetchCompaniesWithLevel(user)
    } yield accesses
            .map(_._1)
            .head

  private def upsertUserAccess(companyId: UUID, userId: UUID, level: AccessLevel) =
    UserAccessTableQuery.insertOrUpdate(UserAccess(
      companyId = companyId,
      userId = userId,
      level = level,
      updateDate = OffsetDateTime.now
    ))

  def setUserLevel(company: Company, user: User, level: AccessLevel): Future[Unit] =
    db.run(upsertUserAccess(company.id, user.id, level)).map(_ => Unit)

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

  def applyToken(token: AccessToken, user: User): Future[Boolean] = {
    if (!token.valid || token.expirationDate.filter(_.isBefore(OffsetDateTime.now)).isDefined)
      Future(false)
    else db.run(DBIO.seq(
          upsertUserAccess(token.companyId, user.id, token.level),
          AccessTokenTableQuery.filter(_.id === token.id).map(_.valid).update(false)
        ).transactionally)
        .map(_ => true)
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
