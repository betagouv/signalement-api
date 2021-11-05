package repositories

import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import slick.jdbc.JdbcProfile
import utils.Constants.Departments
import utils.SIREN
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
          _,
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

  private val substr = SimpleFunction.ternary[String, Int, Int, String]("substr")

  implicit val AccessLevelColumnType = MappedColumnType.base[AccessLevel, String](_.value, AccessLevel.fromValue)

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

  def searchWithReportsCount(
      departments: Seq[String] = Nil,
      activityCodes: Seq[String] = Nil,
      identity: Option[SearchCompanyIdentity] = None,
      offset: Option[Long],
      limit: Option[Int]
  ): Future[PaginatedResult[(Company, Int, Int)]] = {
    val query = companyTableQuery
      .joinLeft(ReportTables.tables)
      .on(_.id === _.companyId)
      .filterIf(departments.nonEmpty) { case (company, _) =>
        company.department.map(a => a.inSet(departments)).getOrElse(false)
      }
      .filterIf(activityCodes.nonEmpty) { case (company, _) =>
        company.activityCode.map(a => a.inSet(activityCodes)).getOrElse(false)
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
                    Report2Status.values.map(_.entryName)
                  ) Then a.id
                }
              )
            )
            .countDefined: Rep[Int]
        )
      }
      .sortBy(_._2.desc)
    val filterQuery = identity
      .map {
        case SearchCompanyIdentityRCS(q)   => query.filter(_._1.id.asColumnOf[String] like s"%${q}%")
        case SearchCompanyIdentitySiret(q) => query.filter(_._1.siret === SIRET(q))
        case SearchCompanyIdentitySiren(q) => query.filter(_._1.siret.asColumnOf[String] like s"${q}_____")
        case SearchCompanyIdentityName(q)  => query.filter(_._1.name.toLowerCase like s"%${q.toLowerCase}%")
        case id: SearchCompanyIdentityId   => query.filter(_._1.id === id.value)
      }
      .getOrElse(query)

    toPaginate(filterQuery, offset, limit)
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

  def findBySiret(siret: SIRET): Future[Option[Company]] =
    db.run(companyTableQuery.filter(_.siret === siret).result.headOption)

  def findBySirets(sirets: List[SIRET]): Future[List[Company]] =
    db.run(companyTableQuery.filter(_.siret inSet sirets).to[List].result)

  def findByName(name: String): Future[List[Company]] =
    db.run(companyTableQuery.filter(_.name.toLowerCase like s"%${name.toLowerCase}%").to[List].result)

  def findBySiren(siren: List[SIREN]): Future[List[Company]] =
    db.run(
      companyTableQuery
        .filter(x => substr(x.siret.asColumnOf[String], 0.bind, 10.bind) inSetBind siren.map(_.value))
        .to[List]
        .result
    )

  def getUserLevel(companyId: UUID, user: User): Future[AccessLevel] =
    db.run(
      UserAccessTableQuery
        .filter(_.companyId === companyId)
        .filter(_.userId === user.id)
        .map(_.level)
        .result
        .headOption
    ).map(_.getOrElse(AccessLevel.NONE))

  def fetchCompaniesWithLevel(user: User): Future[List[CompanyWithAccess]] =
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
    ).map(_.map(x => CompanyWithAccess(x._1, x._2)))

  def fetchUsersWithLevel(companyIds: Seq[UUID]): Future[List[(User, AccessLevel)]] =
    db.run(
      UserAccessTableQuery
        .join(userRepository.userTableQuery)
        .on(_.userId === _.id)
        .filter(_._1.companyId inSet companyIds)
        .filter(_._1.level =!= AccessLevel.NONE)
        .sortBy(entry => (entry._1.level, entry._2.email))
        .map(r => (r._2, r._1.level))
        .distinct
        .to[List]
        .result
    )

  private[this] def fetchUsersAndAccessesByCompanies(
      companyIds: List[UUID],
      levels: Seq[AccessLevel]
  ): Future[List[(UUID, User)]] =
    db.run(
      (for {
        access <- UserAccessTableQuery if access.level.inSet(levels) && (access.companyId inSetBind companyIds)
        user <- userRepository.userTableQuery if user.id === access.userId
      } yield (access.companyId, user)).to[List].result
    )

  def fetchUsersByCompanies(
      companyIds: List[UUID],
      levels: Seq[AccessLevel] = Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
  ): Future[List[User]] =
    fetchUsersAndAccessesByCompanies(companyIds, levels).map(_.map(_._2))

  def fetchAdminsMapByCompany(
      companyIds: List[UUID],
      levels: Seq[AccessLevel] = Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
  ): Future[Map[UUID, List[User]]] =
    fetchUsersAndAccessesByCompanies(companyIds, levels).map(users =>
      users.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
    )

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
