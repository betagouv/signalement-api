package repositories

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import com.github.tminglei.slickpg.trgm.PgTrgmSupport
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
    with PgTrgmSupport
    with PgRangeSupport {

  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI
      extends ExtPostgresAPI
      with ArrayImplicits
      with JsonImplicits
      with Date2DateTimeImplicitsDuration
      with PgTrgmImplicits
      with SimpleSearchPlainImplicits
      with SearchImplicits
      with SearchAssistants
      with RangeImplicits {

    implicit val strListTypeMapper: DriverJdbcType[List[String]] = new SimpleArrayJdbcType[String]("text").to(_.toList)

    val SubstrSQLFunction    = SimpleFunction.ternary[String, Int, Int, String]("substr")
    val SubstrOptSQLFunction = SimpleFunction.ternary[Option[String], Int, Int, Option[String]]("substr")

    val SplitPartSQLFunction = SimpleFunction.ternary[String, String, Int, String]("split_part")

    val ReplaceSQLFunction = SimpleFunction.ternary[String, String, String, String]("replace")

    val DatePartSQLFunction = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

    val ArrayToStringSQLFunction = SimpleFunction.ternary[List[String], String, String, String]("array_to_string")

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
