package controllers

import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.UUID
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv

import utils.AppSpec

import models._
import repositories._

class DummyDatabaseSpec extends Specification with AppSpec {

  lazy val userRepository = injector.instanceOf[UserRepository]
  val userToto = User(
                  UUID.randomUUID(), "toto", "password",
                  None, Some("Prénom"), Some("Nom"), Some("pro@signalconso.beta.gouv.fr"),
                  UserRoles.Pro
                )

  override def setupData() {
    userRepository.create(userToto)
  }
  
  def is = s2"""

 This is a specification to check the UserRepository

 The UserRepository string should
   list 1 user                                                   $e1
   let toto be deleted                                           $e2
   list 0 user                                                   $e3
                                                                 """

  def e1 = Await.result(userRepository.list, 1.second) must have size(1)
  def e2 = Await.result(userRepository.delete(userToto.id), 1.second) must beEqualTo(1)
  def e3 = Await.result(userRepository.list, 1.second) must have size(0)
}
