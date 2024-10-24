package repositories.bookmark

import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
// import utils.Constants.ActionEvent.EMAIL_PRO_NEW_REPORT
// import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BookmarkRepository(
    override val dbConfig: DatabaseConfig[JdbcProfile]
)(implicit override val ec: ExecutionContext)
    extends CRUDRepository[BookmarkTable, Bookmark]
    with BookmarkRepositoryInterface {

  override val table = BookmarkTable.table
  import dbConfig._

  override def deleteByIds(reportId: UUID, userId: UUID): Future[_] = db
    .run(
      table
        .filter(_.reportId === reportId)
        .filter(_.userId === userId)
        .delete
    )
  override def countForUser(userId: UUID): Future[Int] = db
    .run(
      table
        .filter(_.userId === userId)
        .length
        .result
    )

}
