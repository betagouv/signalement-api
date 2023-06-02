package repositories.signalconsoreview

import models.report.signalconsoreview.SignalConsoReview
import models.report.signalconsoreview.SignalConsoReviewId
import repositories.TypedCRUDRepository
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import SignalConsoReviewColumnType._

import scala.concurrent.ExecutionContext

class SignalConsoReviewRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends TypedCRUDRepository[SignalConsoReviewTable, SignalConsoReview, SignalConsoReviewId]
    with SignalConsoReviewRepositoryInterface {

  override val table = SignalConsoReviewTable.table

}
