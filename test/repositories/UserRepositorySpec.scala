package repositories

import scala.concurrent.Await
import scala.concurrent.duration._
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers

import utils.AppSpec
import utils.Fixtures

class UserRepositorySpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val userRepository = injector.instanceOf[UserRepository]
  val userToto = Fixtures.genProUser.sample.get

  override def setupData() = {
    Await.result(userRepository.create(userToto), Duration.Inf)
    ()
  }

  def is = s2"""

 This is a specification to check the UserRepository

 The UserRepository string should
   find user by id                                               $e1
   find user by login                                            $e2
   let the user be deleted                                       $e3
   and then it should not be found                               $e4
                                                                 """

  def e1 = userRepository.get(userToto.id) must beSome.await
  def e2 = userRepository.findByLogin(userToto.email.value) must beSome.await
  def e3 = userRepository.delete(userToto.id) must beEqualTo(1).await
  def e4 = userRepository.get(userToto.id) must beNone.await
}
