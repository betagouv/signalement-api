package repositories.signalconsoreview

import models.report.reportmetadata.Os
import models.report.signalconsoreview.SignalConsoEvaluation
import models.report.signalconsoreview.SignalConsoReviewId
import play.api.Logger
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

import java.util.UUID

object SignalConsoReviewColumnType {

  val logger: Logger = Logger(this.getClass)

  implicit val SignalConsoEvaluationColumnType =
    MappedColumnType.base[SignalConsoEvaluation, Int](
      _.value,
      SignalConsoEvaluation.withValue
    )

  implicit val OsType =
    MappedColumnType.base[Os, String](
      _.entryName,
      Os.namesToValuesMap
    )

  implicit val SignalConsoReviewIdColumnType: JdbcType[SignalConsoReviewId] with BaseTypedType[SignalConsoReviewId] =
    MappedColumnType.base[SignalConsoReviewId, UUID](
      _.value,
      SignalConsoReviewId(_)
    )

}
