package controllers

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.mutable.Specification
import org.specs2.matcher.FutureMatchers
import play.api.libs.json.Json
import play.api.test._
import play.api.test.Helpers._
import utils.silhouette.auth.AuthEnv

import utils.AppSpec
import utils.Fixtures

import models._
import repositories._

class BaseAccessControllerSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {
  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val companyAccessRepository = injector.instanceOf[CompanyAccessRepository]

  val proAdminUser = Fixtures.genProUser.sample.get
  val proMemberUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get
  
  override def setupData = {
    Await.result(for {
      admin <- userRepository.create(proAdminUser)
      member <- userRepository.create(proMemberUser)
      c <- companyRepository.getOrCreate(company.siret, company)
      _ <- companyAccessRepository.setUserLevel(c, admin, AccessLevel.ADMIN)
      _ <- companyAccessRepository.setUserLevel(c, member, AccessLevel.MEMBER)
    } yield Unit,
    Duration.Inf)
  }
  override def configureFakeModule(): AbstractModule = {
    new FakeModule
  }

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.get.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq(proAdminUser, proMemberUser).map(
    user => loginInfo(user) -> user
  ))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }
}

class ListAccessSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec { override def is = s2"""

The listAccesses endpoint should
  list accesses for an admin                        $e1
  be denied for a non admin                         $e2
                                                    """
  def e1 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret).toString)
                  .withAuthenticator[AuthEnv](loginInfo(proAdminUser))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(
      Json.parse(
        s"""
        [
          {
            "userId":"${proAdminUser.id}",
            "email":"${proAdminUser.email.get}",
            "firstName":"${proAdminUser.firstName.get}",
            "lastName":"${proAdminUser.lastName.get}",
            "level":"admin"
          },
          {
            "userId":"${proMemberUser.id}",
            "email":"${proMemberUser.email.get}",
            "firstName":"${proMemberUser.firstName.get}",
            "lastName":"${proMemberUser.lastName.get}",
            "level":"member"
          }]
        """
      )
    )
  }
  def e2 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret).toString)
                  .withAuthenticator[AuthEnv](loginInfo(proMemberUser))
    val result = route(app, request).get
    status(result) must beEqualTo(NOT_FOUND)
  }
}

class InvitationWorkflowSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec { override def is = s2"""

The invitation workflow should
  Let an admin send invitation by email             $e1
  Create a token in database                        $e2
  Let an anonymous visitor check the token          $e3
                                                    """
  val invitedEmail = "test@example.com"
  var tokenString: String = null

  def e1 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendInvitation(company.siret).toString)
                  .withAuthenticator[AuthEnv](loginInfo(proAdminUser))
                  .withBody(Json.obj("email" -> invitedEmail, "level" -> "member"))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }
  def e2 = {
    val tokens = companyAccessRepository.fetchTokens(company)
    tokens.map(_.foreach(t => {tokenString = t.token}))
    tokens.map(_.length) must beEqualTo(1).await
  }

  def e3 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.fetchTokenInfo(company.siret, tokenString).toString)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(
      Json.obj(
        "token" -> tokenString,
        "companySiret" -> company.siret,
        "emailedTo" -> invitedEmail
      )
    )
  }
}
