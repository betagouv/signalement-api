package repositories

import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import utils.Constants.Departments
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyTable(tag: Tag) extends Table[Company](tag, "companies") {
  def id = column[UUID]("id", O.PrimaryKey)
  def siret = column[SIRET]("siret", O.Unique)
  def creationDate = column[OffsetDateTime]("creation_date")
  def name = column[String]("name")
  def streetNumber = column[Option[String]]("street_number")
  def street = column[Option[String]]("street")
  def addressSupplement = column[Option[String]]("address_supplement")
  def city = column[Option[String]]("city")
  def postalCode = column[Option[String]]("postal_code")
  def department = column[Option[String]]("department_old_version")
  def activityCode = column[Option[String]]("activity_code")

  type CompanyData = (
      UUID,
      SIRET,
      OffsetDateTime,
      String,
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String]
  )

  def constructCompany: CompanyData => Company = {
    case (
          id,
          siret,
          creationDate,
          name,
          streetNumber,
          street,
          addressSupplement,
          postalCode,
          city,
          department,
          activityCode
        ) =>
      Company(
        id = id,
        siret = siret,
        creationDate = creationDate,
        name = name,
        address = Address(
          number = streetNumber,
          street = street,
          addressSupplement = addressSupplement,
          postalCode = postalCode,
          city = city
        ),
        activityCode = activityCode
      )
  }

  def extractCompany: PartialFunction[Company, CompanyData] = {
    case Company(
          id,
          siret,
          creationDate,
          name,
          address,
          activityCode
        ) =>
      (
        id,
        siret,
        creationDate,
        name,
        address.number,
        address.street,
        address.addressSupplement,
        address.postalCode,
        address.city,
        address.postalCode.flatMap(Departments.fromPostalCode),
        activityCode
      )
  }

  def * = (
    id,
    siret,
    creationDate,
    name,
    streetNumber,
    street,
    addressSupplement,
    postalCode,
    city,
    department,
    activityCode
  ) <> (constructCompany, extractCompany.lift)
}

object CompanyTables {
  val tables = TableQuery[CompanyTable]
}

@Singleton
class CompanyRepository @Inject() (dbConfigProvider: DatabaseConfigProvider, val userRepository: UserRepository)(
    implicit ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  val companyTableQuery = CompanyTables.tables

  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel.fromValue(_))

  class UserAccessTable(tag: Tag) extends Table[UserAccess](tag, "company_accesses") {
    def companyId = column[UUID]("company_id")
    def userId = column[UUID]("user_id")
    def level = column[AccessLevel]("level")
    def updateDate = column[OffsetDateTime]("update_date")
    def pk = primaryKey("pk_company_user", (companyId, userId))
    def * = (companyId, userId, level, updateDate) <> (UserAccess.tupled, UserAccess.unapply)

    def company = foreignKey("COMPANY_FK", companyId, companyTableQuery)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
    def user = foreignKey("USER_FK", userId, userRepository.userTableQuery)(
      _.id,
      onUpdate = ForeignKeyAction.Cascade,
      onDelete = ForeignKeyAction.Cascade
    )
  }

  val UserAccessTableQuery = TableQuery[UserAccessTable]

  def migration_getTodoSIRET(): Future[Option[String]] =
    db.run(sql"select siret from companies where done is not true and siret is not null LIMIT 1".as[String])
      .flatMap { result =>
        result.headOption match {
          case Some(siret) => Future(Some(siret))
          case None =>
            db.run(
              sql"select siret from reports where done is not true and company_siret is not null LIMIT 1".as[String]
            ).map(_.headOption)
        }
      }

  def migration_update(siret: String, dataOpt: Option[CompanyData]) =
    dataOpt match {
      case Some(data) =>
        val department = data.codePostalEtablissement.map(_.slice(0, 2))
        val street_number = data.numeroVoieEtablissement
        val street = (
          data.typeVoieEtablissement.flatMap(TypeVoies.getByShortName).getOrElse("") + " " +
            data.libelleVoieEtablissement.getOrElse("")
        ).trim()
        val address_supplement = data.complementAdresseEtablissement
        val city = data.libelleCommuneEtablissement
        val postal_code = data.codePostalEtablissement
        val activity_code = data.activitePrincipaleEtablissement
        db.run(
          DBIO.seq(
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
          )
        )
      case None =>
        db.run(
          DBIO.seq(
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
          )
        )
    }

  def searchWithReportsCount(
      departments: Seq[String] = List(),
      identity: Option[String] = None,
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[CompanyWithNbReports]] = {
    val query = companyTableQuery
      .joinLeft(ReportTables.tables)
      .on(_.id === _.companyId)
      .filterIf(departments.nonEmpty) { case (company, report) =>
        company.department.map(a => a.inSet(departments)).getOrElse(false)
      }
      .groupBy(_._1)
      .map { case (grouped, all) => (grouped, all.map(_._2).map(_.map(_.id)).countDefined) }
      .sortBy(_._2.desc)
    val filterQuery = identity
      .map {
        case q if q.matches("[a-zA-Z0-9]{8}-[a-zA-Z0-9]{4}") =>
          query.filter(_._1.id.asColumnOf[String] like s"${q.toLowerCase}%")
        case q if q.matches("[0-9]{14}") => query.filter(_._1.siret === SIRET(q))
        case q if q.matches("[0-9]{9}")  => query.filter(_._1.siret.asColumnOf[String] like s"${q}_____")
        case q                           => query.filter(_._1.name.toLowerCase like s"%${q.toLowerCase}%")
      }
      .getOrElse(query)
    toPaginate(filterQuery, offset, limit).map(res =>
      res.copy(entities = res.entities.map { case (company, count) => CompanyWithNbReports(company, count) })
    )
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
    db.run(companyTableQuery.filter(_.siret === siret).result.headOption)
      .flatMap(
        _.map(Future(_)).getOrElse(db.run(companyTableQuery returning companyTableQuery += data))
      )

  def update(company: Company): Future[Company] = {
    val queryCompany =
      for (refCompany <- companyTableQuery if refCompany.id === company.id)
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
    db.run(
      UserAccessTableQuery
        .filter(_.companyId === companyId)
        .filter(_.userId === user.id)
        .map(_.level)
        .result
        .headOption
    ).map(_.getOrElse(AccessLevel.NONE))

  def fetchCompaniesWithLevel(user: User): Future[List[(Company, AccessLevel)]] =
    db.run(
      UserAccessTableQuery
        .join(companyTableQuery)
        .on(_.companyId === _.id)
        .filter(_._1.userId === user.id)
        .filter(_._1.level =!= AccessLevel.NONE)
        .sortBy(_._1.updateDate.desc)
        .map(r => (r._2, r._1.level))
        .to[List]
        .result
    )

  def fetchUsersWithLevel(company: Company): Future[List[(User, AccessLevel)]] =
    db.run(
      UserAccessTableQuery
        .join(userRepository.userTableQuery)
        .on(_.userId === _.id)
        .filter(_._1.companyId === company.id)
        .filter(_._1.level =!= AccessLevel.NONE)
        .sortBy(entry => (entry._1.level, entry._2.email))
        .map(r => (r._2, r._1.level))
        .to[List]
        .result
    )

  def fetchAdminsByCompany(companyIds: Seq[UUID]): Future[Map[UUID, List[User]]] =
    db.run(
      (for {
        access <- UserAccessTableQuery if access.level === AccessLevel.ADMIN && (access.companyId inSetBind companyIds)
        user <- userRepository.userTableQuery if user.id === access.userId
      } yield (access.companyId, user))
        .to[List]
        .result
    ).map(_.groupBy(_._1).view.mapValues(_.map(_._2)).toMap)

  def fetchAdmins(companyId: UUID): Future[List[User]] =
    db.run(
      UserAccessTableQuery
        .join(userRepository.userTableQuery)
        .on(_.userId === _.id)
        .filter(_._1.companyId === companyId)
        .filter(_._1.level === AccessLevel.ADMIN)
        .map(_._2)
        .to[List]
        .result
    )

  def upsertUserAccess(companyId: UUID, userId: UUID, level: AccessLevel) =
    UserAccessTableQuery.insertOrUpdate(
      UserAccess(
        companyId = companyId,
        userId = userId,
        level = level,
        updateDate = OffsetDateTime.now
      )
    )

  def setUserLevel(company: Company, user: User, level: AccessLevel): Future[Unit] =
    db.run(upsertUserAccess(company.id, user.id, level)).map(_ => ())
}
