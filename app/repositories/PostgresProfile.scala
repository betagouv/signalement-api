package repositories

import java.time.OffsetDateTime

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import com.github.tminglei.slickpg.trgm.PgTrgmSupport

trait PostgresProfile extends ExPostgresProfile
  with PgPlayJsonSupport
  with PgArraySupport
  with PgDate2Support
  with PgAggFuncSupport
  with PgTrgmSupport {

  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI extends API
    with ArrayImplicits
    with JsonImplicits
    with DateTimeImplicits
    with PgTrgmImplicits {

    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

  }
  override protected def computeCapabilities: Set[slick.basic.Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate
}

object PostgresProfile extends PostgresProfile