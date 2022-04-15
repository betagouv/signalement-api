package tasks.account

import akka.actor.ActorSystem
import config.InactiveAccountsTaskConfiguration
import models.AsyncFileKind
import models.Subscription
import models.User
import models.event.Event
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.EventRepository
import repositories.SubscriptionRepository
import repositories.UserRepository
import repositories.asyncfiles.AsyncFileRepository
import utils.AppSpec
import utils.Constants.ActionEvent.CONTROL
import utils.Constants.EventType
import utils.Fixtures

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class InactiveAccountTaskSpec(implicit ee: ExecutionEnv)
    extends org.specs2.mutable.Specification
    with AppSpec
    with Results
    with FutureMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val asyncFileRepository = injector.instanceOf[AsyncFileRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]
  lazy val subscriptionRepository = injector.instanceOf[SubscriptionRepository]
  lazy val inactiveDgccrfAccountRemoveTask = injector.instanceOf[InactiveDgccrfAccountRemoveTask]
  lazy val actorSystem = injector.instanceOf[ActorSystem]

  "InactiveAccountTask" should {

    "remove inactive DGCCRF and subscriptions accounts only" in {

      val conf = InactiveAccountsTaskConfiguration(startTime = LocalTime.now(), inactivePeriod = Period.ofYears(1))
      val now: LocalDateTime = LocalDateTime.now()
      val expirationDateTime: LocalDateTime = LocalDateTime.now().minusYears(conf.inactivePeriod.getYears).minusDays(1)
      new WithApplication(app) {

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
        val inactiveUserSubscriptionUserId: Subscription =
          Subscription(email = None, userId = Some(inactiveDGCCRFUser.id), frequency = Period.ofDays(1))

        // Subscriptions that should be kept
        val activeUserSubscriptionUserId: Subscription =
          Subscription(email = None, userId = Some(activeDGCCRFUser.id), frequency = Period.ofDays(1))

        val inactiveUserEvent = createEvent(inactiveDGCCRFUser)
        val activeUserEvent = createEvent(activeDGCCRFUser)

        val (userList, activeSubscriptionList, inactiveSubscriptionList, events, inactivefiles, activefiles) =
          Await.result(
            for {
              _ <- userRepository.create(inactiveDGCCRFUser)
              _ <- userRepository.create(inactiveProUser)
              _ <- userRepository.create(inactiveAdminUser)
              _ <- userRepository.create(activeDGCCRFUser)
              _ <- userRepository.create(activeProUser)
              _ <- userRepository.create(activeAdminUser)

              _ <- subscriptionRepository.create(inactiveUserSubscriptionUserId)
              _ <- asyncFileRepository.create(inactiveDGCCRFUser, AsyncFileKind.Reports)
              _ <- eventRepository.createEvent(inactiveUserEvent)

              _ <- subscriptionRepository.create(activeUserSubscriptionUserId)
              _ <- asyncFileRepository.create(activeDGCCRFUser, AsyncFileKind.Reports)
              _ <- eventRepository.createEvent(activeUserEvent)

              _ <- new InactiveAccountTask(app.actorSystem, inactiveDgccrfAccountRemoveTask, conf)
                .runTask(now.atOffset(ZoneOffset.UTC))
              userList <- userRepository.list
              activeSubscriptionList <- subscriptionRepository.list(activeDGCCRFUser.id)
              inactiveSubscriptionList <- subscriptionRepository.list(inactiveDGCCRFUser.id)
              events <- eventRepository.list
              inactivefiles <- asyncFileRepository.list(inactiveDGCCRFUser)
              activefiles <- asyncFileRepository.list(activeDGCCRFUser)
            } yield (userList, activeSubscriptionList, inactiveSubscriptionList, events, inactivefiles, activefiles),
            Duration.Inf
          )

        // Validating user
        userList.map(_.id).containsSlice(expectedUsers.map(_.id)) shouldEqual true
        userList.map(_.id).contains(inactiveDGCCRFUser.id) shouldEqual false

        // Validating subscriptions
        activeSubscriptionList
          .map(_.id)
          .containsSlice(
            Seq(activeUserSubscriptionUserId.id)
          ) shouldEqual true

        inactiveSubscriptionList.isEmpty shouldEqual true
        activeSubscriptionList.contains(activeUserSubscriptionUserId) shouldEqual true

        // Validating events
        events.filter(_.userId == inactiveUserEvent.userId) shouldEqual Seq.empty
        events.filter(_.userId == activeUserEvent.userId) shouldEqual Seq(activeUserEvent)

        // Validating async files
        inactivefiles shouldEqual List.empty
        activefiles.size shouldEqual 1

      }

    }

  }

  def createEvent(user: User) =
    Event(
      id = UUID.randomUUID(),
      reportId = None,
      companyId = None,
      userId = Some(user.id),
      creationDate = OffsetDateTime.now(),
      eventType = EventType.DGCCRF,
      action = CONTROL
    )

}
