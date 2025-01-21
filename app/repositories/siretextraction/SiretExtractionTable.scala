package repositories.siretextraction

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.lifted.ProvenShape
import tasks.website.ExtractionResultApi
import tasks.website.SiretExtractionApi

class SiretExtractionTable(tag: Tag) extends Table[ExtractionResultApi](tag, "siret_extractions") {

  implicit val ListSiretExtractionApiColumnType
      : JdbcType[List[SiretExtractionApi]] with BaseTypedType[List[SiretExtractionApi]] =
    MappedColumnType.base[List[SiretExtractionApi], JsValue](
      Json.toJson,
      _.as[List[SiretExtractionApi]]
    )

  def host        = column[String]("host", O.PrimaryKey)
  def status      = column[String]("status")
  def error       = column[Option[String]]("error")
  def extractions = column[Option[List[SiretExtractionApi]]]("extractions")

  override def * : ProvenShape[ExtractionResultApi] = (
    host,
    status,
    error,
    extractions
  ) <> ((ExtractionResultApi.apply _).tupled, ExtractionResultApi.unapply)
}

object SiretExtractionTable {
  val table = TableQuery[SiretExtractionTable]
}
