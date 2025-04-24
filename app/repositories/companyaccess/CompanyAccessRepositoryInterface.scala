package repositories.companyaccess
import models.User
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyAccessCreationInput
import models.company.CompanyWithAccess
import models.company.UserAccess
import repositories.PostgresProfile
import slick.dbio.Effect
import slick.sql.FixedSqlAction

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.Future

trait CompanyAccessRepositoryInterface {

  def getUserLevel(companyId: UUID, user: User): Future[AccessLevel]

  def getUserAccesses(companyIds: List[UUID], userId: UUID): Future[List[UserAccess]]

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

  def countAccesses(companyIds: List[UUID]): Future[Map[UUID, Int]]

  def createAccess(companyId: UUID, userId: UUID, level: AccessLevel): Future[Int]

  def createMultipleUserAccesses(accesses: List[CompanyAccessCreationInput]): Future[Unit]

  def setUserLevel(company: Company, user: User, level: AccessLevel): Future[Unit]

  def proFirstActivationCount(
      ticks: Int = 12
  ): Future[Vector[(Timestamp, Int)]]

  def createCompanyAccessWithoutRun(
      companyId: UUID,
      userId: UUID,
      level: AccessLevel
  ): FixedSqlAction[Int, PostgresProfile.api.NoStream, Effect.Write]

  def removeAccessesIfExist(companiesIds: List[UUID], usersIds: List[UUID]): Future[Unit]

}
