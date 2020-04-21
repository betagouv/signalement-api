package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.{EmailAddress, SIRET}


case class DraftSubscription (
                          departments: List[String],
                          categories: List[ReportCategory],
                          sirets: List[SIRET]
                        )


object DraftSubscription {
  implicit val draftSubscriptionFormat: OFormat[DraftSubscription] = Json.format[DraftSubscription]
}

case class Subscription (
                          id: UUID,
                          userId: Option[UUID],
                          email: Option[EmailAddress],
                          departments: List[String],
                          categories: List[ReportCategory],
                          sirets: List[SIRET]
                        )


object Subscription {
  implicit val subscriptionFormat: OFormat[Subscription] = Json.format[Subscription]
}

