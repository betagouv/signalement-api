package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.{Address, SIRET}
import models._
import utils.Constants.Departments

@Singleton
class CompanyRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider, val userRepository: UserRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  class CompanyTable(tag: Tag) extends Table[Company](tag, "companies") {
    def id = column[UUID]("id", O.PrimaryKey)
    def siret = column[SIRET]("siret", O.Unique)
    def creationDate = column[OffsetDateTime]("creation_date")
    def name = column[String]("name")
    def address = column[Address]("address_old_version")
    def postalCode = column[Option[String]]("postal_code_old_version")
    def department = column[Option[String]]("department_old_version")
    def activityCode = column[Option[String]]("activity_code")

    type CompanyData = (UUID, SIRET, OffsetDateTime, String, Address, Option[String], Option[String], Option[String])

    def constructCompany: CompanyData => Company = {
      case (id, siret, creationDate, name, address, postalCode, _, activityCode) => Company(id, siret, creationDate, name, address, postalCode, activityCode)
    }

    def extractCompany: PartialFunction[Company, CompanyData] = {
      case Company(id, siret, creationDate, name, address, postalCode, activityCode) => (id, siret, creationDate, name, address, postalCode, postalCode.map(Departments.fromPostalCode(_)).flatten, activityCode)
    }

    def * = (id, siret, creationDate, name, address, postalCode, department, activityCode) <> (constructCompany, extractCompany.lift)
  }

  val companyTableQuery = TableQuery[CompanyTable]

  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel.fromValue(_))

  class UserAccessTable(tag: Tag) extends Table[UserAccess](tag, "company_accesses") {
    def companyId = column[UUID]("company_id")
    def userId = column[UUID]("user_id")
    def level = column[AccessLevel]("level")
    def updateDate = column[OffsetDateTime]("update_date")
    def pk = primaryKey("pk_company_user", (companyId, userId))
    def * = (companyId, userId, level, updateDate) <> (UserAccess.tupled, UserAccess.unapply)

    def company = foreignKey("COMPANY_FK", companyId, companyTableQuery)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)
    def user = foreignKey("USER_FK", userId, userRepository.userTableQuery)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)
  }

  val UserAccessTableQuery = TableQuery[UserAccessTable]

  def migration_getTodoSIRET(): Future[Option[String]] = {
    db.run(sql"select siret from companies where done is null and siret is not null LIMIT 1".as[String]).map(_.headOption)
  }

  def migration_update(siret: String, dataOpt: Option[CompanyData]) = {
    dataOpt match {
      case Some(data) => {
        val department = data.codePostalEtablissement.map(_.slice(0, 2))
        val street_number = data.numeroVoieEtablissement
        val street = data.libelleVoieEtablissement
        val address_supplement = data.complementAdresseEtablissement
        val city = data.libelleCommuneEtablissement
        val postal_code = data.codePostalEtablissement
        val activity_code = data.activitePrincipaleEtablissement
        db.run(DBIO.seq(
          sqlu"""
            update companies set
            department = ${department},
            street_number = ${street_number},
            street = ${street},
            address_supplement = ${address_supplement},
            city = ${city},
            postal_code = ${postal_code},
            activity_code = ${activity_code},
            done = true
            where siret = ${siret}
          """,
          sqlu"""
            update reports set
            company_postal_code = ${postal_code},
            company_street_number = ${street_number},
            company_street = ${street},
            company_address_supplement = ${address_supplement},
            company_city = ${city},
            done = true
            where company_siret = ${siret}
          """
        ))
      }
      case None => {
        db.run(DBIO.seq(
          sqlu"""
            update companies set
            done = true
            where siret = ${siret}
          """,
          sqlu"""
            update reports set
            done = true
            where company_siret = ${siret}
          """
        ))
      }
    }

  }

  def getOrCreate(siret: SIRET, data: Company): Future[Company] =
    db.run(companyTableQuery.filter(_.siret === siret).result.headOption).flatMap(
      _.map(Future(_)).getOrElse(db.run(companyTableQuery returning companyTableQuery += data))
    )

  def update(company: Company): Future[Company] = {
    val queryCompany = for (refCompany <- companyTableQuery if refCompany.id === company.id)
      yield refCompany
    db.run(queryCompany.update(company))
      .map(_ => company)
  }

  def fetchCompany(id: UUID) =
    db.run(companyTableQuery.filter(_.id === id).result.headOption)

  def fetchCompanies(companyIds: List[UUID]): Future[List[Company]] =
    db.run(companyTableQuery.filter(_.id inSetBind companyIds).to[List].result)

  def findByShortId(id: String): Future[List[Company]] =
    db.run(companyTableQuery.filter(_.id.asColumnOf[String] like s"${id.toLowerCase}%").to[List].result)

  def findBySiret(siret: SIRET): Future[Option[Company]] =
    db.run(companyTableQuery.filter(_.siret === siret).result.headOption)

  def findBySirets(sirets: Seq[SIRET]): Future[Seq[Company]] =
    db.run(companyTableQuery.filter(_.siret inSet sirets).result)

  def findByName(name: String): Future[List[Company]] =
    db.run(companyTableQuery.filter(_.name.toLowerCase like s"%${name.toLowerCase}%").to[List].result)

  def getUserLevel(companyId: UUID, user: User): Future[AccessLevel] =
    db.run(UserAccessTableQuery
      .filter(_.companyId === companyId)
      .filter(_.userId === user.id)
      .map(_.level)
      .result
      .headOption
    ).map(_.getOrElse(AccessLevel.NONE))

  def fetchCompaniesWithLevel(user: User): Future[List[(Company, AccessLevel)]] =
    db.run(UserAccessTableQuery.join(companyTableQuery).on(_.companyId === _.id)
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
        access <- UserAccessTableQuery if access.level === AccessLevel.ADMIN && (access.companyId inSetBind companyIds)
        user <- userRepository.userTableQuery if user.id === access.userId
      } yield (access.companyId, user))
        .to[List]
        .result
    ).map(_.groupBy(_._1).mapValues(_.map(_._2)))
  }

  def fetchAdmins(companyId: UUID): Future[List[User]] =
    db.run(UserAccessTableQuery.join(userRepository.userTableQuery).on(_.userId === _.id)
      .filter(_._1.companyId === companyId)
      .filter(_._1.level === AccessLevel.ADMIN)
      .map(_._2)
      .to[List]
      .result
    )

  def upsertUserAccess(companyId: UUID, userId: UUID, level: AccessLevel) =
    UserAccessTableQuery.insertOrUpdate(UserAccess(
      companyId = companyId,
      userId = userId,
      level = level,
      updateDate = OffsetDateTime.now
    ))

  def setUserLevel(company: Company, user: User, level: AccessLevel): Future[Unit] =
    db.run(upsertUserAccess(company.id, user.id, level)).map(_ => Unit)
}
