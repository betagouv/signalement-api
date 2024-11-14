package orchestrators

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.mvc.Results
import play.api.test.WithApplication
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class UserOrchestratorTest(implicit ee: ExecutionEnv)
    extends org.specs2.mutable.Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp(
  )

  lazy val userRepository   = components.userRepository
  lazy val userOrchestrator = components.userOrchestrator

  "UserOrchestrator" should {

    "soft delete a user" in {

      new WithApplication(app) {

        val targetUser  = Fixtures.genUser.sample.get
        val currentUser = Fixtures.genUser.sample.get
        val otherUser   = Fixtures.genUser.sample.get

        val expectedUsers = Seq(currentUser, otherUser)

        val (userList, deletedUsersList) =
          Await.result(
            for {
              _ <- userRepository.create(targetUser)
              _ <- userRepository.create(currentUser)
              _ <- userRepository.create(otherUser)

              _ <- userOrchestrator.softDelete(targetUserId = targetUser.id, currentUserId = currentUser.id)

              userList         <- userRepository.list()
              deletedUsersList <- userRepository.listDeleted()

            } yield (
              userList,
              deletedUsersList
            ),
            Duration.Inf
          )

        userList.map(_.id).toSet equals (expectedUsers.map(_.id).toSet) shouldEqual true
        userList.map(_.id).contains(targetUser.id) shouldEqual false

        deletedUsersList.map(_.id).contains(targetUser.id) shouldEqual true

      }

    }

  }

}
