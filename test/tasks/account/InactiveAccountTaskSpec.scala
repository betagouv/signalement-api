package tasks.account

import akka.actor.ActorSystem
import config.InactiveAccountsTaskConfiguration
import models.Subscription
import models.User
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.SubscriptionRepository
import repositories.UserRepository
import utils.AppSpec
import utils.Fixtures

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.ZoneOffset
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class InactiveAccountTaskSpec(implicit ee: ExecutionEnv)
    extends org.specs2.mutable.Specification
    with AppSpec
    with Results
    with FutureMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val subscriptionRepository = injector.instanceOf[SubscriptionRepository]
  lazy val actorSystem = injector.instanceOf[ActorSystem]

  class FakeModule(inactiveAccountsTaskConfiguration: InactiveAccountsTaskConfiguration) extends AppFakeModule {
    override def configure() =
      super.configure
//      bind[InactiveAccountTask].toInstance(new InactiveAccountTask(actorSystem, userRepository, subscriptionRepository, inactiveAccountsTaskConfiguration))
  }

  def app(inactiveAccountsTaskConfiguration: InactiveAccountsTaskConfiguration) =
    new GuiceApplicationBuilder()
      .overrides(new FakeModule(inactiveAccountsTaskConfiguration))
      .build()

  "InactiveAccountTask" should {

    "remove inactive DGCCRF and subscriptions accounts" in {

      val conf = InactiveAccountsTaskConfiguration(startTime = LocalTime.now(), inactivePeriod = Period.ofYears(1))
      val now: LocalDateTime = LocalDateTime.now()
      val expirationDateTime: LocalDateTime = LocalDateTime.now().minusYears(conf.inactivePeriod.getYears).minusDays(1)
      new WithApplication(app(conf)) {

        // Inactive account to be removed
        val inactiveDGCCRFUser: User = Fixtures.genDgccrfUser.sample.get
          .copy(lastEmailValidation = Some(expirationDateTime.atOffset(ZoneOffset.UTC)))

        // Other kinds of users that should be kept
        val inactiveProUser: User =
          Fixtures.genProUser.sample.get.copy(lastEmailValidation = Some(expirationDateTime.atOffset(ZoneOffset.UTC)))
        val inactiveAdminUser: User =
          Fixtures.genAdminUser.sample.get.copy(lastEmailValidation = Some(expirationDateTime.atOffset(ZoneOffset.UTC)))
        val activeDGCCRFUser: User =
          Fixtures.genDgccrfUser.sample.get.copy(lastEmailValidation = Some(now.atOffset(ZoneOffset.UTC)))
        val activeProUser: User =
          Fixtures.genProUser.sample.get.copy(lastEmailValidation = Some(now.atOffset(ZoneOffset.UTC)))
        val activeAdminUser: User =
          Fixtures.genAdminUser.sample.get.copy(lastEmailValidation = Some(now.atOffset(ZoneOffset.UTC)))

        val expectedUsers = Seq(inactiveProUser, inactiveAdminUser, activeDGCCRFUser, activeProUser, activeAdminUser)

        // Inactive subscriptions that should be deleted
        val inactiveUserSubscriptionEmail: Subscription =
          Subscription(email = Some(inactiveDGCCRFUser.email), userId = None, frequency = Period.ofDays(1))
        val inactiveUserSubscriptionUserId: Subscription =
          Subscription(email = None, userId = Some(inactiveDGCCRFUser.id), frequency = Period.ofDays(1))

        // Subscriptions that should be kept
        val activeUserSubscriptionEmail: Subscription =
          Subscription(email = Some(activeDGCCRFUser.email), userId = None, frequency = Period.ofDays(1))
        val activeUserSubscriptionUserId: Subscription =
          Subscription(email = None, userId = Some(activeDGCCRFUser.id), frequency = Period.ofDays(1))

        val (userList, activeSubscriptionList, inactiveSubscriptionList) = Await.result(
          for {
            _ <- userRepository.create(inactiveDGCCRFUser)
            _ <- userRepository.create(inactiveProUser)
            _ <- userRepository.create(inactiveAdminUser)
            _ <- userRepository.create(activeDGCCRFUser)
            _ <- userRepository.create(activeProUser)
            _ <- userRepository.create(activeAdminUser)
            _ <- subscriptionRepository.create(inactiveUserSubscriptionUserId)
            _ <- subscriptionRepository.create(inactiveUserSubscriptionEmail)
            _ <- subscriptionRepository.create(activeUserSubscriptionEmail)
            _ <- subscriptionRepository.create(activeUserSubscriptionUserId)
            _ <- new InactiveAccountTask(app.actorSystem, userRepository, subscriptionRepository, conf).runTask(now)
            userList <- userRepository.list
            activeSubscriptionList <- subscriptionRepository.list(activeDGCCRFUser.id)
            inactiveSubscriptionList <- subscriptionRepository.list(inactiveDGCCRFUser.id)
          } yield (userList, activeSubscriptionList, inactiveSubscriptionList),
          Duration.Inf
        )

        userList.containsSlice(expectedUsers) shouldEqual true
        userList.contains(inactiveDGCCRFUser) shouldEqual false

        activeSubscriptionList.containsSlice(
          Seq(activeUserSubscriptionEmail, activeUserSubscriptionUserId)
        ) shouldEqual true
        inactiveSubscriptionList.isEmpty shouldEqual true
        activeSubscriptionList.contains(activeUserSubscriptionEmail) shouldEqual true
        activeSubscriptionList.contains(activeUserSubscriptionUserId) shouldEqual true
      }

    }

  }

}
