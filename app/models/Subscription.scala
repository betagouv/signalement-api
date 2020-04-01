package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}
import utils.EmailAddress


case class Subscription (
                          id: Option[UUID],
                          userId: Option[UUID],
                          email: Option[EmailAddress],
                          departments: List[String],
                          categories: List[ReportCategory]
                        )


object Subscription {

  implicit val subscriptionFormat: OFormat[Subscription] = Json.format[Subscription]

}

