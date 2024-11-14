package tasks.account

import config.InactiveAccountsTaskConfiguration
import models.AsyncFile
import models.AsyncFileKind
import models.Subscription
import models.User
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.tasklock.TaskRepositoryInterface
import utils.AppSpec
import utils.Fixtures
import utils.TaskRepositoryMock
import utils.TestApp

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class InactiveAccountTaskSpec(implicit ee: ExecutionEnv)
    extends org.specs2.mutable.Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp(
  )

  val taskRepositoryMock: TaskRepositoryInterface = new TaskRepositoryMock()

  lazy val userRepository                    = components.userRepository
  lazy val asyncFileRepository               = components.asyncFileRepository
  lazy val subscriptionRepository            = components.subscriptionRepository
  lazy val inactiveDgccrfAccountRemoveTask   = components.inactiveDgccrfAccountRemoveTask
  lazy val inactiveDgccrfAccountReminderTask = components.inactiveDgccrfAccountReminderTask
//  lazy val actorSystem = components.actorSystem

  "InactiveAccountTask" should {

    "remove inactive DGCCRF and subscriptions accounts only" in {

      val conf = components.applicationConfiguration.task.copy(inactiveAccounts =
        InactiveAccountsTaskConfiguration(
          startTime = LocalTime.now().truncatedTo(ChronoUnit.MILLIS),
          inactivePeriod = Period.ofYears(1),
          firstReminder = Period.ofMonths(9),
          secondReminder = Period.ofMonths(11)
        )
      )
      val now: LocalDateTime = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS)
      val expirationDateTime: LocalDateTime =
        LocalDateTime
          .now()
          .truncatedTo(ChronoUnit.MILLIS)
          .minusYears(conf.inactiveAccounts.inactivePeriod.getYears.toLong)
          .minusDays(1L)
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
          Subscription(
            email = None,
            creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            userId = Some(inactiveDGCCRFUser.id),
            frequency = Period.ofDays(1)
          )

        // Subscriptions that should be kept
        val activeUserSubscriptionUserId: Subscription =
          Subscription(
            email = None,
            creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
            userId = Some(activeDGCCRFUser.id),
            frequency = Period.ofDays(1)
          )

        val (userList, deletedUsersList, activeSubscriptionList, inactiveSubscriptionList, inactivefiles, activefiles) =
          Await.result(
            for {
              _ <- userRepository.create(inactiveDGCCRFUser)
              _ <- userRepository.create(inactiveProUser)
              _ <- userRepository.create(inactiveAdminUser)
              _ <- userRepository.create(activeDGCCRFUser)
              _ <- userRepository.create(activeProUser)
              _ <- userRepository.create(activeAdminUser)

              _ <- subscriptionRepository.create(inactiveUserSubscriptionUserId)
              _ <- asyncFileRepository.create(AsyncFile.build(inactiveDGCCRFUser, AsyncFileKind.Reports))
              _ <- subscriptionRepository.create(activeUserSubscriptionUserId)
              _ <- asyncFileRepository.create(AsyncFile.build(activeDGCCRFUser, AsyncFileKind.Reports))

              _ <- new InactiveAccountTask(
                this.app.actorSystem,
                inactiveDgccrfAccountRemoveTask,
                inactiveDgccrfAccountReminderTask,
                conf,
                taskRepositoryMock
              )
                .runTask(now.atOffset(ZoneOffset.UTC))
              userList                 <- userRepository.list()
              deletedUsersList         <- userRepository.listDeleted()
              activeSubscriptionList   <- subscriptionRepository.list(activeDGCCRFUser.id)
              inactiveSubscriptionList <- subscriptionRepository.list(inactiveDGCCRFUser.id)
              inactivefiles            <- asyncFileRepository.list(inactiveDGCCRFUser)
              activefiles              <- asyncFileRepository.list(activeDGCCRFUser)
            } yield (
              userList,
              deletedUsersList,
              activeSubscriptionList,
              inactiveSubscriptionList,
              inactivefiles,
              activefiles
            ),
            Duration.Inf
          )

        // Validating user
        userList.map(_.id).toSet shouldEqual (expectedUsers.map(_.id).toSet)
        userList.map(_.id).contains(inactiveDGCCRFUser.id) shouldEqual false
        deletedUsersList.map(_.id).contains(inactiveDGCCRFUser.id) shouldEqual true

        // Validating subscriptions
        activeSubscriptionList
          .map(_.id)
          .containsSlice(
            Seq(activeUserSubscriptionUserId.id)
          ) shouldEqual true

        inactiveSubscriptionList.isEmpty shouldEqual true

        activeSubscriptionList.contains(activeUserSubscriptionUserId) shouldEqual true

        // Validating async files
        inactivefiles shouldEqual List.empty
        activefiles.size shouldEqual 1

      }

    }

  }

}
