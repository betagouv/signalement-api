package repositories

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import com.github.tminglei.slickpg.trgm.PgTrgmSupport
import models.report.ReportTag
import slick.ast.Library.SqlAggregateFunction
import slick.ast.TypedType
import slick.lifted.FunctionSymbolExtensionMethods.functionSymbolExtensionMethods

import java.time.OffsetDateTime

trait PostgresProfile
    extends ExPostgresProfile
    with PgPlayJsonSupport
    with PgArraySupport
    with PgSearchSupport
    with PgDate2Support
    with PgAggFuncSupport
    with PgTrgmSupport {

  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI
      extends API
      with ArrayImplicits
      with JsonImplicits
      with DateTimeImplicits
      with PgTrgmImplicits
      with SimpleSearchPlainImplicits
      with SearchImplicits
      with SearchAssistants {

    implicit val strListTypeMapper: DriverJdbcType[List[String]] = new SimpleArrayJdbcType[String]("text").to(_.toList)

    val SubstrSQLFunction = SimpleFunction.ternary[String, Int, Int, String]("substr")

    val DatePartSQLFunction = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

    val ArrayToStringSQLFunction = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")
    SimpleFunction.binary[List[ReportTag], Int, Int]("array_length")

    SimpleFunction.binary[Option[Double], Option[Double], Option[Double]]("least")

    // Declare the name of an aggregate function:
    val CountGroupBy = new SqlAggregateFunction("count")

    // Implement the aggregate function as an extension method:
    implicit class ArrayAggColumnQueryExtensionMethods[P, C[_]](val q: Query[Rep[P], _, C]) {
      def countGroupBy[B](implicit tm: TypedType[B]) =
        CountGroupBy.column[B](q.toNode)
    }

  }
  override protected def computeCapabilities: Set[slick.basic.Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate
}

object PostgresProfile extends PostgresProfile
