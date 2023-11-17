package repositories.subscription

import models.Subscription
import models.User
import repositories.CRUDRepositoryInterface
import utils.EmailAddress

import java.time.Period
import java.util.UUID
import scala.concurrent.Future

trait SubscriptionRepositoryInterface extends CRUDRepositoryInterface[Subscription] {

  def list(userId: UUID): Future[List[Subscription]]

  def findFor(user: User, id: UUID): Future[Option[Subscription]]

  def deleteFor(user: User, id: UUID): Future[Int]

  def listForFrequency(frequency: Period): Future[List[(Subscription, Option[EmailAddress])]]

  def getDirectionDepartementaleEmail(department: String): Future[Seq[EmailAddress]]
}
