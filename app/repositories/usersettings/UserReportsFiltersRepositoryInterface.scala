package repositories.usersettings

import models.UserReportsFilters

import java.util.UUID
import scala.concurrent.Future

trait UserReportsFiltersRepositoryInterface {
  def createOrUpdate(element: UserReportsFilters): Future[UserReportsFilters]

  def get(userId: UUID, name: String): Future[Option[UserReportsFilters]]

  def list(userId: UUID): Future[List[UserReportsFilters]]

  def delete(userId: UUID, name: String): Future[Unit]

  def rename(userId: UUID, oldName: String, newName: String): Future[Unit]

  def setAsDefault(userId: UUID, name: String): Future[Unit]

  def unsetDefault(userId: UUID, name: String): Future[Unit]
}
