package models

import java.util.UUID

import play.api.libs.json.{Json, OFormat}


case class Subscription (
                  id: Option[UUID],
                  userId: Option[UUID],
                  category: String,
                  values: List[String]
                )


object Subscription {

  implicit val subscriptionFormat: OFormat[Subscription] = Json.format[Subscription]

}

