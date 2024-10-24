package repositories.bookmark

import repositories.CRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

trait BookmarkRepositoryInterface extends CRUDRepositoryInterface[Bookmark] {

  def deleteByIds(reportId: UUID, userId: UUID): Future[_]

  def countForUser(userId: UUID): Future[Int]
}
