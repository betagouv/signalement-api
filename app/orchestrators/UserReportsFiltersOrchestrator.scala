package orchestrators

import models.UserReportsFilters
import play.api.libs.json.JsValue
import repositories.usersettings.UserReportsFiltersRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

class UserReportsFiltersOrchestrator(userReportsFiltersRepository: UserReportsFiltersRepositoryInterface) {
  def save(userId: UUID, name: String, filters: JsValue): Future[UserReportsFilters] =
    userReportsFiltersRepository.createOrUpdate(UserReportsFilters(userId, name, filters))

  def get(userId: UUID, name: String): Future[Option[UserReportsFilters]] =
    userReportsFiltersRepository.get(userId, name)

  def list(userId: UUID): Future[List[UserReportsFilters]] =
    userReportsFiltersRepository.list(userId)

  def delete(userId: UUID, name: String): Future[Unit] =
    userReportsFiltersRepository.delete(userId, name)

  def rename(userId: UUID, oldName: String, newName: String): Future[Unit] =
    userReportsFiltersRepository.rename(userId, oldName, newName)

  def setAsDefault(userId: UUID, name: String): Future[Unit] =
    userReportsFiltersRepository.setAsDefault(userId, name)

  def unsetDefault(userId: UUID, name: String): Future[Unit] =
    userReportsFiltersRepository.unsetDefault(userId, name)

}
