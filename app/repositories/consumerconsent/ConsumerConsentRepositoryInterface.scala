package repositories.consumerconsent

import models.consumerconsent.ConsumerConsent
import models.consumerconsent.ConsumerConsentId
import repositories.TypedCRUDRepositoryInterface
import utils.EmailAddress

import scala.concurrent.Future

trait ConsumerConsentRepositoryInterface extends TypedCRUDRepositoryInterface[ConsumerConsent, ConsumerConsentId] {

  def removeConsent(emailAddress: EmailAddress) : Future[Unit]
}
