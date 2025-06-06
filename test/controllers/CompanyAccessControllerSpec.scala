package controllers

import models._
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyActivationAttempt
import models.token.TokenKind.CompanyInit
import models.token.TokenKind.CompanyJoin
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.http.Status.FORBIDDEN
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test._
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.temporal.ChronoUnit
import java.time.OffsetDateTime
import java.time.{Duration => JavaDuration}
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.AuthHelpers._

class BaseAccessControllerSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  val proAdminUser     = Fixtures.genProUser.sample.get
  val proMemberUser    = Fixtures.genProUser.sample.get
  val proUnrelatedUser = Fixtures.genProUser.sample.get

  val (app, components) = TestApp.buildApp(
  )
  override def afterAll(): Unit = {
    app.stop()
    ()
  }

  lazy val userRepository                     = components.userRepository
  lazy val companyRepository                  = components.companyRepository
  lazy val companyAccessRepository            = components.companyAccessRepository
  lazy val accessTokenRepository              = components.accessTokenRepository
  lazy val companyActivationAttemptRepository = components.companyActivationAttemptRepository

  val company = Fixtures.genCompany.sample.get.copy(isHeadOffice = true)

  override def setupData() =
    Await.result(
      for {
        admin  <- userRepository.create(proAdminUser)
        member <- userRepository.create(proMemberUser)
        _      <- userRepository.create(proUnrelatedUser)
        (c, _) <- companyRepository.getOrCreate(company.siret, company)
        _      <- companyAccessRepository.createAccess(c.id, admin.id, AccessLevel.ADMIN)
        _      <- companyAccessRepository.createAccess(c.id, member.id, AccessLevel.MEMBER)
      } yield (),
      Duration.Inf
    )

}

class ListAccessSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {
  override def is = s2"""

The listAccesses endpoint should
  list accesses for an admin                        $e1
  list accesses for a member                        $e1
  be denied for an unrelated user                         $e3
                                                    """

  def e1 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret.value).toString)
      .withAuthCookie(proAdminUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(listAccessesJson)
  }

  def e2 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret.value).toString)
      .withAuthCookie(proMemberUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(listAccessesJson)
  }
  def e3 = {
    val request = FakeRequest(GET, routes.CompanyAccessController.listAccesses(company.siret.value).toString)
      .withAuthCookie(proUnrelatedUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(FORBIDDEN)
  }

  private val listAccessesJson = Json.parse(
    s"""
    [
      {
        "userId":"${proAdminUser.id}",
        "email":"${proAdminUser.email}",
        "firstName":"${proAdminUser.firstName}",
        "lastName":"${proAdminUser.lastName}",
        "level":"admin",
        "editable": false
      },
      {
        "userId":"${proMemberUser.id}",
        "email":"${proMemberUser.email}",
        "firstName":"${proMemberUser.firstName}",
        "lastName":"${proMemberUser.lastName}",
        "level":"member",
        "editable": true
      }]
    """
  )
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
  val invitedEmail                 = "test@example.com"
  var invitationToken: AccessToken = null

  def e1 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendInvitation(company.siret.value).toString)
      .withAuthCookie(proAdminUser.email, components.cookieAuthenticator)
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
      .withAuthCookie(proAdminUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    contentAsJson(result) must beEqualTo(
      Json.toJson(
        List(
          Map(
            "id"        -> invitationToken.id.toString,
            "level"     -> "member",
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
        "token"        -> invitationToken.token,
        "kind"         -> "COMPANY_JOIN",
        "companySiret" -> company.siret,
        "emailedTo"    -> invitedEmail
      )
    )
  }

  def e5 = {
    val latestToken = Await.result(accessTokenRepository.fetchPendingTokens(company).map(_.head), Duration.Inf)
    latestToken.id must beEqualTo(invitationToken.id)
    latestToken.expirationDate.get must beGreaterThan(invitationToken.expirationDate.get)
  }
}

class NewCompanyActivationWithNoAdminSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {

  override def is = s2"""

  Given company not registered with activation code sent by postal mail $e1
  And an initial token to join the company    $e2
  when user activate account                  $e4
  Then activation token should still be valid $e5
  Then access should not be created           $e6
  Then user creation account token should exist $e7
  Then no activation attempt should be registered $e8
                                              """

  val newCompany         = Fixtures.genCompany.sample.get
  val newProUser         = Fixtures.genProUser.sample.get
  var token: AccessToken = null

  def e1 = {
    val (company, _) = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    token = Await.result(
      accessTokenRepository
        .create(AccessToken.build(CompanyInit, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None)),
      Duration.Inf
    )
    token must haveClass[AccessToken]
  }

  def e4 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendActivationLink(newCompany.siret.value).toString)
      .withBody(Json.obj("token" -> "123456", "email" -> newProUser.email.value))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }

  def e5 = {
    val invalidToken = Await.result(accessTokenRepository.get(token.id), Duration.Inf)
    invalidToken.map(_.valid) shouldEqual (Some(true))
  }

  def e6 = {
    val admins = Await.result(companyAccessRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List.empty)
  }

  def e7 = {
    val userCreationToken = Await.result(accessTokenRepository.fetchPendingTokens(newProUser.email), Duration.Inf)
    userCreationToken.length shouldEqual 1
    userCreationToken.headOption.map(_.kind) shouldEqual (Some(CompanyJoin))
    userCreationToken.headOption.map(_.valid) shouldEqual (Some(true))
  }
  def e8 = {
    val nbAttempts =
      Await.result(companyActivationAttemptRepository.countAttempts(newCompany.siret.value, 30.minutes), Duration.Inf)
    nbAttempts shouldEqual 0
  }

}

class NewCompanyActivationOnUserWithExistingCreationAccountTokenSpec(implicit ee: ExecutionEnv)
    extends BaseAccessControllerSpec {

  override def is = s2"""
  Given company not registered with activation code sent by postal mail $e1
  And an initial token to join the company    $e2
  and an existing ${CompanyJoin.entryName} token for that user  $e8
  when user activate account                  $e4
  Then activation token should still be valid $e5
  Then access should not be created           $e6
  Then user creation account token should exist $e7
  Then no activation attempt should be registered $e9
                                              """

  val newCompany                            = Fixtures.genCompany.sample.get
  val existingProUser                       = Fixtures.genProUser.sample.get
  var companyActivationToken: AccessToken   = null
  var initialUserCreationToken: AccessToken = null
  var initialUserTokenValidity              = JavaDuration.ofMinutes(1)

  def e1 = {
    val (company, _) = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    companyActivationToken = Await.result(
      accessTokenRepository
        .create(AccessToken.build(CompanyInit, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None)),
      Duration.Inf
    )
    companyActivationToken must haveClass[AccessToken]
  }

  def e8 = {
    initialUserCreationToken = Await.result(
      accessTokenRepository
        .create(
          AccessToken.build(
            kind = CompanyJoin,
            token = UUID.randomUUID().toString,
            validity = Some(initialUserTokenValidity),
            companyId = Some(newCompany.id),
            level = Some(AccessLevel.ADMIN),
            emailedTo = Some(existingProUser.email)
          )
        ),
      Duration.Inf
    )
    initialUserCreationToken must haveClass[AccessToken]
  }

  def e4 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendActivationLink(newCompany.siret.value).toString)
      .withBody(Json.obj("token" -> "123456", "email" -> existingProUser.email.value))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }

  def e5 = {
    val invalidToken = Await.result(accessTokenRepository.get(companyActivationToken.id), Duration.Inf)
    invalidToken.map(_.valid) shouldEqual (Some(true))
  }

  def e6 = {
    val admins = Await.result(companyAccessRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List.empty)
  }

  def e7 = {
    val userCreationTokenList =
      Await.result(accessTokenRepository.fetchPendingTokens(existingProUser.email), Duration.Inf)
    userCreationTokenList.length shouldEqual 1
    userCreationTokenList.headOption.map(_.kind) shouldEqual (Some(CompanyJoin))
    userCreationTokenList.headOption.map(_.valid) shouldEqual (Some(true))
    userCreationTokenList.headOption.map(_.id) shouldEqual (Some(initialUserCreationToken.id))
    userCreationTokenList.headOption.flatMap(
      _.expirationDate.map(
        _.isAfter(OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).plus(initialUserTokenValidity))
      )
    ) shouldEqual Some(true)
  }

  def e9 = {
    val nbAttempts =
      Await.result(companyActivationAttemptRepository.countAttempts(newCompany.siret.value, 30.minutes), Duration.Inf)
    nbAttempts shouldEqual 0
  }

}

class NewCompanyActivationOnExistingUserSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {

  override def is = s2"""

  Given company not registered with activation code sent by postal mail $e1
  And an initial token to join the company    $e2
  and an already existing user                $e3
  when user activate account                  $e4
  Then token should be not valid anymore      $e5
  Then access should be created               $e6
  Then no activation attempt should be registered $e7
                                              """

  val newCompany         = Fixtures.genCompany.sample.get
  val existingProUser    = Fixtures.genProUser.sample.get
  var token: AccessToken = null

  def e1 = {
    val (company, _) = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    token = Await.result(
      accessTokenRepository
        .create(AccessToken.build(CompanyInit, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None)),
      Duration.Inf
    )
    token must haveClass[AccessToken]
  }

  def e3 = {
    val user = Await.result(userRepository.create(existingProUser), Duration.Inf)
    user must haveClass[User]
  }

  def e4 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendActivationLink(newCompany.siret.value).toString)
      .withBody(Json.obj("token" -> "123456", "email" -> existingProUser.email.value))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }

  def e5 = {
    val invalidToken = Await.result(accessTokenRepository.get(token.id), Duration.Inf)
    invalidToken.map(_.valid) shouldEqual (Some(false))
  }

  def e6 = {
    val admins = Await.result(companyAccessRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List(existingProUser.id))
  }

  def e7 = {
    val nbAttempts =
      Await.result(companyActivationAttemptRepository.countAttempts(newCompany.siret.value, 30.minutes), Duration.Inf)
    nbAttempts shouldEqual 0
  }
}

class NewCompanyActivationWithWrongTokenSpec(implicit ee: ExecutionEnv) extends BaseAccessControllerSpec {

  override def is = s2"""
  Given company not registered with activation code sent by postal mail $e1
  And an initial token to join the company    $e2
  when user activate account with a WRONG token $e4
  Then activation token should still be valid $e5
  Then access should not be created           $e6
  Then user creation account token should NOT exist $e7
  Then there should be ONE activation attempt $e8
                                              """

  val newCompany         = Fixtures.genCompany.sample.get
  val newProUser         = Fixtures.genProUser.sample.get
  var token: AccessToken = null

  def e1 = {
    val (company, _) = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    token = Await.result(
      accessTokenRepository
        .create(AccessToken.build(CompanyInit, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None)),
      Duration.Inf
    )
    token must haveClass[AccessToken]
  }

  def e4 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendActivationLink(newCompany.siret.value).toString)
      .withBody(Json.obj("token" -> "999999", "email" -> newProUser.email.value))
    val result = route(app, request).get
    status(result) must beEqualTo(NOT_FOUND)
  }

  def e5 = {
    val invalidToken = Await.result(accessTokenRepository.get(token.id), Duration.Inf)
    invalidToken.map(_.valid) shouldEqual (Some(true))
  }

  def e6 = {
    val admins = Await.result(companyAccessRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List.empty)
  }

  def e7 = {
    val userCreationToken = Await.result(accessTokenRepository.fetchPendingTokens(newProUser.email), Duration.Inf)
    userCreationToken.length shouldEqual 0
  }
  def e8 = {
    val nbAttempts =
      Await.result(companyActivationAttemptRepository.countAttempts(newCompany.siret.value, 30.minutes), Duration.Inf)
    nbAttempts shouldEqual 1
  }

}

class NewCompanyActivationWithWrongTokenAndTooManyAttemptsSpec(implicit ee: ExecutionEnv)
    extends BaseAccessControllerSpec {

  override def is = s2"""
  Given company not registered with activation code sent by postal mail $e1
  And an initial token to join the company    $e2
  And 20 past activation attempts in the last 30 minutes $e3
  when user activate account with a WRONG token $e4
  Then activation token should still be valid $e5
  Then access should not be created           $e6
  Then user creation account token should NOT exist $e7
  Then there should be still 20 activation attempts $e8
                                              """

  val newCompany         = Fixtures.genCompany.sample.get
  val newProUser         = Fixtures.genProUser.sample.get
  var token: AccessToken = null

  def e1 = {
    val (company, _) = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    token = Await.result(
      accessTokenRepository
        .create(AccessToken.build(CompanyInit, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None)),
      Duration.Inf
    )
    token must haveClass[AccessToken]
  }

  def e3 = {
    Await
      .result(
        Future.sequence(
          for {
            _ <- 0 until 20
          } yield companyActivationAttemptRepository.create(
            CompanyActivationAttempt(UUID.randomUUID, newCompany.siret.value, OffsetDateTime.now.minusMinutes(5))
          )
        ),
        Duration.Inf
      )
      .toList
    true must beTrue
  }

  def e4 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.sendActivationLink(newCompany.siret.value).toString)
      .withBody(Json.obj("token" -> "999999", "email" -> newProUser.email.value))
    val result = route(app, request).get
    status(result) must beEqualTo(FORBIDDEN)
  }

  def e5 = {
    val invalidToken = Await.result(accessTokenRepository.get(token.id), Duration.Inf)
    invalidToken.map(_.valid) shouldEqual (Some(true))
  }

  def e6 = {
    val admins = Await.result(companyAccessRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List.empty)
  }

  def e7 = {
    val userCreationToken = Await.result(accessTokenRepository.fetchPendingTokens(newProUser.email), Duration.Inf)
    userCreationToken.length shouldEqual 0
  }
  def e8 = {
    val nbAttempts =
      Await.result(companyActivationAttemptRepository.countAttempts(newCompany.siret.value, 30.minutes), Duration.Inf)
    nbAttempts shouldEqual 20
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

  val newCompany         = Fixtures.genCompany.sample.get
  var token: AccessToken = null
  def e1 = {
    val (company, _) = Await.result(companyRepository.getOrCreate(newCompany.siret, newCompany), Duration.Inf)
    company must haveClass[Company]
  }

  def e2 = {
    token = Await.result(
      accessTokenRepository
        .create(AccessToken.build(CompanyJoin, "123456", None, Some(newCompany.id), Some(AccessLevel.ADMIN), None)),
      Duration.Inf
    )
    token must haveClass[AccessToken]
  }

  def e3 = {
    val request = FakeRequest(POST, routes.CompanyAccessController.acceptToken(newCompany.siret.value).toString)
      .withAuthCookie(proMemberUser.email, components.cookieAuthenticator)
      .withBody(Json.obj("token" -> "123456"))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
  }

  def e4 = {
    val admins = Await.result(companyAccessRepository.fetchAdmins(newCompany.id), Duration.Inf)
    admins.map(_.id) must beEqualTo(List(proMemberUser.id))
  }

  def e5 = {
    val pendingTokens = Await.result(accessTokenRepository.fetchPendingTokens(newCompany), Duration.Inf)
    pendingTokens should beEmpty
  }
}
