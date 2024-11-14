package repositories

import models.User
import models.event.Event
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.libs.json.Json
import utils.AppSpec
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

class UserRepositorySpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  val now = OffsetDateTime.now()

  lazy val userRepository  = components.userRepository
  lazy val eventRepository = components.eventRepository
  val userToto             = Fixtures.genProUser.sample.get
  val activeDgccrfUser     = Fixtures.genDgccrfUser.sample.get.copy(lastEmailValidation = Some(now.minusDays(1)))
  val expiredDgccrfUser    = Fixtures.genDgccrfUser.sample.get.copy(lastEmailValidation = Some(now.minusYears(2)))
  val inactiveDgccrfUser   = Fixtures.genDgccrfUser.sample.get.copy(lastEmailValidation = Some(now.minusMonths(6)))
  val inactiveDgccrfUserWithEmails =
    Fixtures.genDgccrfUser.sample.get.copy(lastEmailValidation = Some(now.minusMonths(6)))
  val inactiveProUser   = Fixtures.genProUser.sample.get.copy(lastEmailValidation = Some(now.minusMonths(6)))
  val inactiveAdminUser = Fixtures.genAdminUser.sample.get.copy(lastEmailValidation = Some(now.minusMonths(6)))

  val user1NotDeleted = Fixtures.genProUser.sample.get
  val user1Deleted    = Fixtures.genProUser.sample.get.copy(email = user1NotDeleted.email, deletionDate = Some(now))
  val user2Deleted1   = Fixtures.genProUser.sample.get.copy(deletionDate = Some(now))
  val user2Deleted2 =
    Fixtures.genProUser.sample.get.copy(email = user2Deleted1.email, deletionDate = Some(now.minusDays(1)))

  override def setupData() = {
    Await.result(userRepository.create(userToto), Duration.Inf)
    Await.result(userRepository.create(activeDgccrfUser), Duration.Inf)
    Await.result(userRepository.create(expiredDgccrfUser), Duration.Inf)
    Await.result(userRepository.create(inactiveDgccrfUser), Duration.Inf)
    Await.result(userRepository.create(inactiveDgccrfUserWithEmails), Duration.Inf)
    Await.result(userRepository.create(inactiveProUser), Duration.Inf)
    Await.result(userRepository.create(inactiveAdminUser), Duration.Inf)

    Await.result(userRepository.create(user1NotDeleted), Duration.Inf)
    Await.result(userRepository.create(user1Deleted), Duration.Inf)
    Await.result(userRepository.create(user2Deleted1), Duration.Inf)
    Await.result(userRepository.create(user2Deleted2), Duration.Inf)

    Await.result(
      eventRepository.create(
        Event(
          id = UUID.randomUUID(),
          reportId = None,
          companyId = None,
          userId = Some(inactiveDgccrfUserWithEmails.id),
          creationDate = now,
          eventType = EventType.DGCCRF,
          action = ActionEvent.EMAIL_INACTIVE_AGENT_ACCOUNT,
          details = Json.obj()
        )
      ),
      Duration.Inf
    )
    Await.result(
      eventRepository.create(
        Event(
          id = UUID.randomUUID(),
          reportId = None,
          companyId = None,
          userId = Some(inactiveDgccrfUserWithEmails.id),
          creationDate = now,
          eventType = EventType.DGCCRF,
          action = ActionEvent.EMAIL_INACTIVE_AGENT_ACCOUNT,
          details = Json.obj()
        )
      ),
      Duration.Inf
    )
    ()
  }

  def is = s2"""

 This is a specification to check the UserRepositoryInterface

 The UserRepositoryInterface string should
   find user by id                                               $e1
   find user by login                                            $e2
   let the user be deleted                                       $e3
   and then it should not be found                               $e4

 listInactiveDGCCRFWithSentEmailCount should
   list only DGCCRF users with email count                       $e6
   not list any active or expired user                           $e7

 findByEmailIncludingDeleted should
   find not deleted first                                        $e8
   order by deletion date desc                                   $e9
                                                                 """

  def e1 = userRepository.get(userToto.id).map(_.isDefined) must beTrue.await
  def e2 = userRepository.findByEmail(userToto.email.value).map(_.isDefined) must beTrue.await
  def e3 = userRepository.delete(userToto.id) must beEqualTo(1).await
  def e4 = userRepository.get(userToto.id).map(_.isDefined) must beFalse.await

  def e6 = userRepository
    .listInactiveAgentsWithSentEmailCount(now.minusMonths(1), now.minusYears(1))
    .map(_.map { case (user, count) => (user.id, count) }.toSet) must beEqualTo(
    List(inactiveDgccrfUserWithEmails.id -> Some(2), inactiveDgccrfUser.id -> None).toSet
  ).await
  def e7 = userRepository
    .listInactiveAgentsWithSentEmailCount(now.minusMonths(1), now.minusMonths(2)) must beEmpty[List[
    (User, Option[Int])
  ]].await

  def e8 = userRepository
    .findByEmailIncludingDeleted(user1Deleted.email.value)
    .map(_.map(user => user.email -> user.deletionDate)) must beSome(
    user1NotDeleted.email -> Option.empty[OffsetDateTime]
  ).await

  def e9 = userRepository
    .findByEmailIncludingDeleted(user2Deleted2.email.value)
    .map(_.map(user => user.email -> user.deletionDate.map(_.truncatedTo(ChronoUnit.SECONDS)))) must beSome(
    user2Deleted1.email -> user2Deleted1.deletionDate.map(_.truncatedTo(ChronoUnit.SECONDS))
  ).await
}
