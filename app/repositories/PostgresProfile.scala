package repositories

import com.github.tminglei.slickpg._
import play.api.libs.json.{JsValue, Json}

trait PostgresProfile extends ExPostgresProfile
  with PgArraySupport
  with PgDate2Support {

  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI extends API
    with ArrayImplicits
    with DateTimeImplicits {
    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)
    implicit val playJsonArrayTypeMapper =
      new AdvancedArrayJdbcType[JsValue](pgjson,
        (s) => utils.SimpleArrayUtils.fromString[JsValue](Json.parse(_))(s).orNull,
        (v) => utils.SimpleArrayUtils.mkString[JsValue](_.toString())(v)
      ).to(_.toList)
  }

}

object PostgresProfile extends PostgresProfile
