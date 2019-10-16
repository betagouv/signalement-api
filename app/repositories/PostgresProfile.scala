package repositories

import com.github.tminglei.slickpg._
import play.api.libs.json.{JsValue, Json}

trait PostgresProfile extends ExPostgresProfile
  with PgPlayJsonSupport
  with PgArraySupport
  with PgDate2Support {

  def pgjson = "jsonb"

  override val api = MyAPI

  object MyAPI extends API
    with ArrayImplicits
    with JsonImplicits
    with DateTimeImplicits {

    implicit val strListTypeMapper = new SimpleArrayJdbcType[String]("text").to(_.toList)

  }

}

object PostgresProfile extends PostgresProfile