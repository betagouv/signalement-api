package repositories.companyaccess
import models.User
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyWithAccess
import repositories.PostgresProfile
import slick.dbio.Effect
import slick.sql.FixedSqlAction

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.Future

trait CompanyAccessRepositoryInterface {

  def getUserLevel(companyId: UUID, user: User): Future[AccessLevel]

  def fetchCompaniesWithLevel(user: User): Future[List[CompanyWithAccess]]

  def fetchUsersWithLevel(companyIds: Seq[UUID]): Future[List[(User, AccessLevel)]]

  def fetchUsersByCompanies(
      companyIds: List[UUID],
      levels: Seq[AccessLevel] = Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
  ): Future[List[User]]

  def fetchUsersByCompanyIds(
      companyIds: List[UUID],
      levels: Seq[AccessLevel] = Seq(AccessLevel.ADMIN, AccessLevel.MEMBER)
  ): Future[Map[UUID, List[User]]]

  def fetchAdmins(companyId: UUID): Future[List[User]]

  def createUserAccess(companyId: UUID, userId: UUID, level: AccessLevel): Future[Int]

  def setUserLevel(company: Company, user: User, level: AccessLevel): Future[Unit]

  def proFirstActivationCount(
      ticks: Int = 12
  ): Future[Vector[(Timestamp, Int)]]

  def createCompanyUserAccessWithoutRun(
      companyId: UUID,
      userId: UUID,
      level: AccessLevel
  ): FixedSqlAction[Int, PostgresProfile.api.NoStream, Effect.Write]

}
