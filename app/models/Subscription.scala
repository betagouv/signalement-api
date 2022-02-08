package models

import models.report.ReportCategory
import models.report.ReportTag
import models.report.ReportTagFilter
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import utils.Country
import utils.EmailAddress
import utils.SIRET

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID

case class SubscriptionCreation(
    departments: List[String],
    categories: List[ReportCategory],
    sirets: List[SIRET],
    tags: List[ReportTag],
    countries: List[String],
    frequency: Period
)

object SubscriptionCreation {

  implicit val subscriptionCreationFormat: OFormat[SubscriptionCreation] = Json.format[SubscriptionCreation]
}

case class SubscriptionUpdate(
    departments: Option[List[String]],
    categories: Option[List[ReportCategory]],
    sirets: Option[List[SIRET]],
    tags: Option[List[ReportTag]],
    countries: Option[List[String]],
    frequency: Option[Period]
)

object SubscriptionUpdate {
  implicit val subscriptionUpdateFormat: OFormat[SubscriptionUpdate] = Json.format[SubscriptionUpdate]
}

case class Subscription(
    id: UUID = UUID.randomUUID,
    creationDate: OffsetDateTime = OffsetDateTime.now,
    userId: Option[UUID],
    email: Option[EmailAddress],
    departments: List[String] = List.empty,
    categories: List[ReportCategory] = List.empty,
    tags: List[ReportTagFilter] = List.empty,
    countries: List[Country] = List.empty,
    sirets: List[SIRET] = List.empty,
    frequency: Period
)

object Subscription {
  implicit val subscriptionFormat: OFormat[Subscription] = Json.format[Subscription]
}
