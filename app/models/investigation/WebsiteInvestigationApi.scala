package models.investigation

import models.website.Website
import models.website.WebsiteId
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
case class WebsiteInvestigationApi(
    id: WebsiteId,
    investigationStatus: Option[InvestigationStatus],
    lastUpdated: Option[OffsetDateTime]
) {

  def copyToDomain(website: Website): Website =
    website.copy(
      investigationStatus = this.investigationStatus.getOrElse(InvestigationStatus.NotProcessed),
      lastUpdated = OffsetDateTime.now()
    )

}

object WebsiteInvestigationApi {

  implicit val WebsiteInvestigationAPIFormat: OFormat[WebsiteInvestigationApi] = Json.format[WebsiteInvestigationApi]

}
