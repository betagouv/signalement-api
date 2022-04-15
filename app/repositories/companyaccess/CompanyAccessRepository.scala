package repositories.companyaccess

import models._
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile.api._
import repositories.company.CompanyTable
import repositories.companyaccess.CompanyAccessColumnType._
import repositories.computeTickValues
import repositories.user.UserTable
import slick.jdbc.JdbcProfile

import java.sql.Timestamp
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyAccessRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  def getUserLevel(companyId: UUID, user: User): Future[AccessLevel] =
    db.run(
      CompanyAccessTable.table
        .filter(_.companyId === companyId)
        .filter(_.userId === user.id)
        .map(_.level)
        .result
        .headOption
    ).map(_.getOrElse(AccessLevel.NONE))

  def fetchCompaniesWithLevel(user: User): Future[List[CompanyWithAccess]] =
    db.run(
      CompanyAccessTable.table
        .join(CompanyTable.table)
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
      CompanyAccessTable.table
        .join(UserTable.table)
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
        access <- CompanyAccessTable.table if access.level.inSet(levels) && (access.companyId inSetBind companyIds)
        user <- UserTable.table if user.id === access.userId
      } yield (access.companyId, user)).to[List].result
    )

  def fetchUsersByCompanies(
      companyIds: List[UUID],
      levels: Seq[AccessLevel] = Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
  ): Future[List[User]] =
    fetchUsersAndAccessesByCompanies(companyIds, levels).map(_.map(_._2))

  def fetchUsersByCompanyId(
      companyIds: List[UUID],
      levels: Seq[AccessLevel] = Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
  ): Future[Map[UUID, List[User]]] =
    fetchUsersAndAccessesByCompanies(companyIds, levels).map(users =>
      users.groupBy(_._1).view.mapValues(_.map(_._2)).toMap
    )

  def fetchAdmins(companyId: UUID): Future[List[User]] =
    db.run(
      CompanyAccessTable.table
        .join(UserTable.table)
        .on(_.userId === _.id)
        .filter(_._1.companyId === companyId)
        .filter(_._1.level === AccessLevel.ADMIN)
        .map(_._2)
        .to[List]
        .result
    )

  def createCompanyUserAccess(companyId: UUID, userId: UUID, level: AccessLevel) =
    CompanyAccessTable.table.insertOrUpdate(
      UserAccess(
        companyId = companyId,
        userId = userId,
        level = level,
        updateDate = OffsetDateTime.now,
        creationDate = OffsetDateTime.now
      )
    )

  def createUserAccess(companyId: UUID, userId: UUID, level: AccessLevel) =
    db.run(createCompanyUserAccess(companyId, userId, level))

  def setUserLevel(company: Company, user: User, level: AccessLevel): Future[Unit] =
    db.run(
      CompanyAccessTable.table
        .filter(_.companyId === company.id)
        .filter(_.userId === user.id)
        .map(companyAccess => (companyAccess.level, companyAccess.updateDate))
        .update((level, OffsetDateTime.now()))
    ).map(_ => ())

  def proFirstActivationCount(
      ticks: Int = 12
  ): Future[Vector[(Timestamp, Int)]] =
    db.run(sql"""select * from (
          select v.a, count(distinct id)
          from (select distinct company_id as id, min(my_date_trunc('month'::text, creation_date)::timestamp) as creation_date
                from company_accesses 
                group by company_id
                order by creation_date desc) as t
          right join
                (SELECT a FROM (VALUES #${computeTickValues(ticks)} ) AS X(a)) as v on t.creation_date = v.a
          group by v.a
          order by 1 DESC
    ) as res order by 1 ASC;    
         """.as[(Timestamp, Int)])

}
