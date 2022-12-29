package repositories

import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import scala.concurrent.Await
import scala.concurrent.duration._

class UserRepositorySpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  val (app, components) = TestApp.buildApp(
    None
  )

  lazy val userRepository = components.userRepository
  val userToto = Fixtures.genProUser.sample.get

  override def setupData() = {
    Await.result(userRepository.create(userToto), Duration.Inf)
    ()
  }

  def is = s2"""

 This is a specification to check the UserRepositoryInterface

 The UserRepositoryInterface string should
   find user by id                                               $e1
   find user by login                                            $e2
   let the user be deleted                                       $e3
   and then it should not be found                               $e4
                                                                 """

  def e1 = userRepository.get(userToto.id).map(_.isDefined) must beTrue.await
  def e2 = userRepository.findByEmail(userToto.email.value).map(_.isDefined) must beTrue.await
  def e3 = userRepository.delete(userToto.id) must beEqualTo(1).await
  def e4 = userRepository.get(userToto.id).map(_.isDefined) must beFalse.await
}
