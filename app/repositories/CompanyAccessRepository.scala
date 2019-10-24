package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import models._

@Singleton
class CompanyAccessRepository @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                     val companyRepository: CompanyRepository, val userRepository: UserRepository)
                                     (implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel(_))

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
    def expirationDate = column[Option[OffsetDateTime]]("expiration_date")
    def * = (id, companyId, token, level, valid, expirationDate) <> (AccessToken.tupled, AccessToken.unapply)

    def company = foreignKey("COMPANY_FK", companyId, companyRepository.companyTableQuery)(_.id, onUpdate=ForeignKeyAction.Cascade, onDelete=ForeignKeyAction.Cascade)
  }

  val AccessTokenTableQuery = TableQuery[AccessTokenTable]

  def getUserLevel(company: Company, user: User): Future[AccessLevel] =
    db.run(UserAccessTableQuery
      .filter(_.companyId === company.id)
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
      validity: Option[java.time.temporal.TemporalAmount]): Future[Unit] =
    db.run(AccessTokenTableQuery += AccessToken(
      id = UUID.randomUUID(),
      companyId = company.id,
      token = token,
      level = level,
      valid = true,
      expirationDate = validity.map(OffsetDateTime.now.plus(_))
    )).map(_ => Unit)

  def findToken(company: Company, token: String): Future[Option[AccessToken]] =
    db.run(AccessTokenTableQuery
      .filter(
        _.expirationDate.filter(_ < OffsetDateTime.now).isEmpty)
      .filter(_.valid)
      .filter(_.companyId === company.id)
      .filter(_.token === token)
      .result
      .headOption
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
}
