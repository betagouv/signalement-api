package repositories

import com.github.tminglei.slickpg._
import com.github.tminglei.slickpg.agg.PgAggFuncSupport
import com.github.tminglei.slickpg.trgm.PgTrgmSupport
import models.report.ReportTag
import models.website.WebsiteKind

import java.time.OffsetDateTime

trait PostgresProfile
    extends ExPostgresProfile
    with PgPlayJsonSupport
    with PgArraySupport
    with PgDate2Support
    with PgAggFuncSupport
    with PgTrgmSupport {

  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI extends API with ArrayImplicits with JsonImplicits with DateTimeImplicits with PgTrgmImplicits {

    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

    implicit val websiteKindListTypeMapper = new SimpleArrayJdbcType[String]("text")
      .mapTo[WebsiteKind](WebsiteKind.fromValue(_), _.value)
      .to(_.toList)

    val date_part = SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

    SimpleFunction.ternary[String, Int, Int, String]("substr")

    SimpleFunction.binary[String, OffsetDateTime, Int]("date_part")

    SimpleFunction.ternary[List[String], String, String, String]("array_to_string")
    SimpleFunction.binary[List[ReportTag], Int, Int]("array_length")

  }
  override protected def computeCapabilities: Set[slick.basic.Capability] =
    super.computeCapabilities + slick.jdbc.JdbcCapabilities.insertOrUpdate
}

object PostgresProfile extends PostgresProfile
