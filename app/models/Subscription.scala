package models

import java.time.Period
import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.{Country, EmailAddress, SIRET}


case class DraftSubscription(
  departments: List[String],
  categories: List[ReportCategory],
  sirets: List[SIRET],
  tags: List[String],
  countries: List[Country],
  frequency: Period
)

object DraftSubscription {
  implicit val draftSubscriptionFormat: OFormat[DraftSubscription] = Json.format[DraftSubscription]
}

case class Subscription(
  id: UUID,
  userId: Option[UUID],
  email: Option[EmailAddress],
  departments: List[String],
  categories: List[ReportCategory],
  tags: List[String],
  countries: List[Country],
  sirets: List[SIRET],
  frequency: Period
)

object Subscription {
  implicit val subscriptionFormat: OFormat[Subscription] = Json.format[Subscription]
}

