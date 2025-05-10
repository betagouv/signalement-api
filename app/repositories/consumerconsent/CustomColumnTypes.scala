package repositories.consumerconsent

import models.consumerconsent.ConsumerConsentId
import slick.jdbc.PostgresProfile.api._

import java.util.UUID

object CustomColumnTypes {
  implicit val consumerConsentIdColumnType: BaseColumnType[ConsumerConsentId] =
    MappedColumnType.base[ConsumerConsentId, UUID](_.value, ConsumerConsentId)

}
