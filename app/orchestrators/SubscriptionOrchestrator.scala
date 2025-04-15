package orchestrators

import models.Subscription
import models.SubscriptionCreation
import models.SubscriptionUpdate
import models.User
import repositories.subscription.SubscriptionRepositoryInterface
import utils.Country

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SubscriptionOrchestrator(subscriptionRepository: SubscriptionRepositoryInterface)(implicit ec: ExecutionContext) {

  def createSubscription(draftSubscription: SubscriptionCreation, user: User): Future[Subscription] =
    subscriptionRepository
      .create(
        Subscription(
          userId = Some(user.id),
          email = None,
          departments = draftSubscription.departments,
          categories = draftSubscription.categories,
          withTags = draftSubscription.withTags,
          withoutTags = draftSubscription.withoutTags,
          countries = draftSubscription.countries.map(Country.fromCode),
          sirets = draftSubscription.sirets,
          websites = draftSubscription.websites,
          phones = draftSubscription.phones,
          frequency = draftSubscription.frequency
        )
      )

  def updateSubscription(uuid: UUID, draftSubscription: SubscriptionUpdate, user: User): Future[Option[Subscription]] =
    for {
      maybeSubscription <- subscriptionRepository.findFor(user, uuid)
      updatedSubscription <- maybeSubscription
        .map(s =>
          subscriptionRepository
            .update(
              s.id,
              s.copy(
                departments = draftSubscription.departments.getOrElse(s.departments),
                categories = draftSubscription.categories.getOrElse(s.categories),
                withTags = draftSubscription.withTags.getOrElse(s.withTags),
                withoutTags = draftSubscription.withoutTags.getOrElse(s.withoutTags),
                countries = draftSubscription.countries
                  .map(_.map(Country.fromCode))
                  .getOrElse(s.countries),
                sirets = draftSubscription.sirets.getOrElse(s.sirets),
                websites = draftSubscription.websites.getOrElse(s.websites),
                phones = draftSubscription.phones.getOrElse(s.phones),
                frequency = draftSubscription.frequency.getOrElse(s.frequency)
              )
            )
            .map(Some(_))
        )
        .getOrElse(Future.successful(None))
    } yield updatedSubscription

  def getSubscriptions(user: User) =
    subscriptionRepository.list(user.id)

  def getSubscription(uuid: UUID, user: User) =
    subscriptionRepository.findFor(user, uuid)

  def removeSubscription(uuid: UUID, user: User) =
    subscriptionRepository.deleteFor(user, uuid)
}
