package tasks.website

import play.api.libs.json.Json
import play.api.libs.json.OFormat
import tasks.company.CompanySearchResult

case class SiretApi(
    siret: String,
    valid: Boolean
)

object SiretApi {
  implicit val format: OFormat[SiretApi] = Json.format[SiretApi]
}

case class SirenApi(
    siren: String,
    valid: Boolean
)

object SirenApi {
  implicit val format: OFormat[SirenApi] = Json.format[SirenApi]
}

case class SiretExtractionApi(
    siret: Option[SiretApi],
    siren: Option[SirenApi],
    links: List[String],
    sirene: Option[CompanySearchResult]
)

object SiretExtractionApi {
  implicit val format: OFormat[SiretExtractionApi] = Json.format[SiretExtractionApi]
}

case class ExtractionResultApi(
    website: String,
    status: String,
    error: Option[String],
    extractions: Option[List[SiretExtractionApi]]
)

object ExtractionResultApi {
  implicit val format: OFormat[ExtractionResultApi] = Json.format[ExtractionResultApi]
}
