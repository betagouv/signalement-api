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

    val LevenshteinFunction = SimpleFunction.binary[String, String, Int]("levenshtein")

    // Declare the name of an aggregate function:
    val CountGroupBy = new SqlAggregateFunction("count")

    // Implement the aggregate function as an extension method:
    implicit class ArrayAggColumnQueryExtensionMethods[P, C[_]](val q: Query[Rep[P], _, C]) {
      def countGroupBy[B](implicit tm: TypedType[B]) =
        CountGroupBy.column[B](q.toNode)
    }

    // Utilisé pour caster un type mal généré par slick : @>
    // Cette fonction attend le même type à gauche et à droite
    // mais slick génère varchar[] @> text[]
    val castVarCharArrayToTextArray = SimpleExpression.unary[List[String], List[String]] { (s, qb) =>
      qb.sqlBuilder += "CAST( ": Unit
      qb.expr(s)
      qb.sqlBuilder += " AS TEXT[])": Unit
    }
  }
  override protected def computeCapabilities: Set[slick.basic.Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate
}

object PostgresProfile extends PostgresProfile
