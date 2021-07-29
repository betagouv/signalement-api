package controllers

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.Environment
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import scala.concurrent.Await
import scala.concurrent.Future
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
  lazy val accessTokenRepository = injector.instanceOf[AccessTokenRepository]

  val proAdminUser = Fixtures.genProUser.sample.get
  val proMemberUser = Fixtures.genProUser.sample.get
  val company = Fixtures.genCompany.sample.get

  override def setupData =
    Await.result(
      for {
        admin <- userRepository.create(proAdminUser)
        member <- userRepository.create(proMemberUser)
        c <- companyRepository.getOrCreate(company.siret, company)
        _ <- companyRepository.setUserLevel(c, admin, AccessLevel.ADMIN)
        _ <- companyRepository.setUserLevel(c, member, AccessLevel.MEMBER)
      } yield Unit,
      Duration.Inf
    )
  override def configureFakeModule(): AbstractModule =
    new FakeModule

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq(proAdminUser, proMemberUser).map(user => loginInfo(user) -> user))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }
}

class ListAccessSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {
  override def is = s2"""

The listAccesses endpoint should
  list accesses for an admin                        $e1
  be denied for a non admin                         $e2
                                                    """
  def e1 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret.value).toString)
      .withAuthenticator[AuthEnv](loginInfo(proAdminUser))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(
      Json.parse(
        s"""
        [
          {
            "userId":"${proAdminUser.id}",
            "email":"${proAdminUser.email}",
            "firstName":"${proAdminUser.firstName}",
            "lastName":"${proAdminUser.lastName}",
            "level":"admin"
          },
          {
            "userId":"${proMemberUser.id}",
            "email":"${proMemberUser.email}",
            "firstName":"${proMemberUser.firstName}",
            "lastName":"${proMemberUser.lastName}",
            "level":"member"
          }]
        """
      )
    )
  }
  def e2 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret.value).toString)
      .withAuthenticator[AuthEnv](loginInfo(proMemberUser))
    val result = route(app, request).get
    status(result) must beEqualTo(NOT_FOUND)
  }
}

class MyCompaniesSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {
  override def is = s2"""

The myCompanies endpoint should
  list my accesses as an admin                      ${checkAccess(proAdminUser, AccessLevel.ADMIN)}
  list my accesses as a basic member                ${checkAccess(proMemberUser, AccessLevel.MEMBER)}
  reject me if I am not connected                   $checkNotConnected
                                                    """
  def checkAccess(user: User, level: AccessLevel) = {
    val request = FakeRequest(GET, routes.CompanyAccessController.myCompanies().toString)
      .withAuthenticator[AuthEnv](loginInfo(user))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(Json.toJson(Seq((company, level))))
  }
  def checkNotConnected = {
    val request = FakeRequest(GET, routes.CompanyAccessController.myCompanies().toString)
    val result = route(app, request).get
    status(result) must beEqualTo(UNAUTHORIZED)
  }
}

class InvitationWorkflowSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {
  override def is = s2"""

The invitation workflow should
  Let an admin send invitation by email             $e1
  Have created a token in database                  $e2
  Show the token in pending invitations             $e3
  Let an anonymous visitor check the token          $e4
  When the same user is invited again               $e1
  Then the token should be updated                  $e5
                                                    """
  val invitedEmail = "test@example.com"
  var invitationToken: AccessToken = null

  def e1 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendInvitation(company.siret.value).toString)
      .withAuthenticator[AuthEnv](loginInfo(proAdminUser))
      .withBody(Json.obj("email" -> invitedEmail, "level" -> "member"))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }

  def e2 = {
    val tokens = accessTokenRepository.fetchPendingTokens(company)
    tokens.map(_.foreach(t => invitationToken = t))
    tokens.map(_.length) must beEqualTo(1).await
  }

  def e3 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listPendingTokens(company.siret.value).toString)
      .withAuthenticator[AuthEnv](loginInfo(proAdminUser))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(
      Json.toJson(
        List(
          Map(
            "id" -> invitationToken.id.toString,
            "level" -> "member",
            "emailedTo" -> invitedEmail,
            "expirationDate" -> invitationToken.expirationDate.get.format(
              java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
            )
          )
        )
      )
    )
  }

  def e4 = {
    val request = FakeRequest(
      GET,
      routes.CompanyAccessController.fetchTokenInfo(company.siret.value, invitationToken.token).toString
    )
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(
      Json.obj(
        "token" -> invitationToken.token,
        "kind" -> "COMPANY_JOIN",
        "companySiret" -> company.siret,
        "emailedTo" -> invitedEmail
      )
    )
  }

  def e5 = {
    val latestToken = Await.result(accessTokenRepository.fetchPendingTokens(company).map(_.head), Duration.Inf)
    latestToken.id must beEqualTo(invitationToken.id)
    latestToken.expirationDate.get must beGreaterThan(invitationToken.expirationDate.get)
  }
}

class UserAcceptTokenSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {
  override def is = s2"""

  Given a new company                         $e1
  And an initial token to join the company    $e2
  An existing user may use the token          $e3
  And then join the company                   $e4
  And the token be used                       $e5
                                              """

  val newCompany = Fixtures.genCompany.sample.get
  var token: AccessToken = null
  def e1 = {
    val company = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    token = Await.result(
      accessTokenRepository
        .createToken(TokenKind.COMPANY_JOIN, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None),
      Duration.Inf
    )
    token must haveClass[AccessToken]
  }

  def e3 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.acceptToken(newCompany.siret.value).toString)
      .withAuthenticator[AuthEnv](loginInfo(proMemberUser))
      .withBody(Json.obj("token" -> "123456"))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }

  def e4 = {
    val admins = Await.result(companyRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List(proMemberUser.id))
  }

  def e5 = {
    val pendingTokens = Await.result(accessTokenRepository.fetchPendingTokens(newCompany), Duration.Inf)
    pendingTokens should beEmpty
  }
}
