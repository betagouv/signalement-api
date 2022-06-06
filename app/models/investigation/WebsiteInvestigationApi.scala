package models.investigation

import models.website.WebsiteId
import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.time.OffsetDateTime
import java.time.ZoneOffset

case class WebsiteInvestigationApi(
    id: Option[WebsiteInvestigationId],
    websiteId: WebsiteId,
    practice: Option[Practice],
    investigationStatus: Option[InvestigationStatus],
    attribution: Option[DepartmentDivision],
    lastUpdated: Option[OffsetDateTime]
) {

  def createOrCopyToDomain(websiteInvestigation: Option[WebsiteInvestigation]): WebsiteInvestigation = {
    val now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)
    websiteInvestigation
      .map(
        _.copy(
          websiteId = this.websiteId,
          practice = this.practice,
          investigationStatus = this.investigationStatus.getOrElse(InvestigationStatus.NotProcessed),
          attribution = this.attribution,
          lastUpdated = now
        )
      )
      .getOrElse(
        new WebsiteInvestigation(
          id = WebsiteInvestigationId.generateId(),
          websiteId = this.websiteId,
          practice = this.practice,
          investigationStatus = this.investigationStatus.getOrElse(InvestigationStatus.NotProcessed),
          attribution = this.attribution,
          creationDate = now,
          lastUpdated = now
        )
      )
  }
}

object WebsiteInvestigationApi {

  implicit val WebsiteInvestigationAPIFormat: OFormat[WebsiteInvestigationApi] = Json.format[WebsiteInvestigationApi]

}
