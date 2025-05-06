package controllers.company

import controllers.routes
import models._
import models.company.AccessLevel
import models.company.Company
import models.report.Report
import models.report.ReportStatus
import models.token.TokenKind.CompanyInit
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.matcher.Matcher
import org.specs2.matcher.TraversableMatchers
import org.specs2.mutable.Specification
import play.api.Logger
import play.api.test.Helpers._
import play.api.test._
import utils.Constants.ActionEvent._
import utils.Constants.EventType._
import utils.AppSpec
import utils.Fixtures
import utils.TestApp
import utils.AuthHelpers._

import java.time.temporal.ChronoUnit
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Random

class BaseFetchCompaniesToActivateSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val ec: ExecutionContext = ee.executionContext
  val logger: Logger                = Logger(this.getClass)

  lazy val userRepository        = components.userRepository
  lazy val companyRepository     = components.companyRepository
  lazy val accessTokenRepository = components.accessTokenRepository
  lazy val eventRepository       = components.eventRepository
  lazy val reportRepository      = components.reportRepository

  val tokenDuration             = java.time.Period.parse("P60D")
  val reportReminderByPostDelay = java.time.Period.parse("P28D")
  val defaultTokenCreationDate  = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusMonths(1)

  val adminUser = Fixtures.genAdminUser.sample.get

  // (company, last notice date, token creation date)
  var expectedCompaniesToActivate: Seq[(Company, Option[OffsetDateTime], OffsetDateTime)] = Seq()

  def createCompanyAndToken = {
    val company = Fixtures.genCompany.sample.get
    for {
      (company, _) <- companyRepository.getOrCreate(company.siret, company)
      token <- accessTokenRepository.create(
        AccessToken.build(
          CompanyInit,
          f"${Random.nextInt(1000000)}%06d",
          Some(tokenDuration),
          Some(company.id),
          Some(AccessLevel.ADMIN),
          None,
          defaultTokenCreationDate
        )
      )
    } yield (company, token)
  }

  def createPendingReport(company: Company): Future[Report] = reportRepository.create(
    Fixtures
      .genReportForCompanyWithStatus(company, ReportStatus.TraitementEnCours)
      .sample
      .get
  )

  def setupCaseWithoutPendingReport =
    for {
      _ <- createCompanyAndToken
    } yield ()

  def setupCaseNewCompany =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
    } yield expectedCompaniesToActivate = expectedCompaniesToActivate :+ ((c, None, defaultTokenCreationDate))

  def setupCaseNewCompanyWithMultiplePendingReports =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
      _      <- createPendingReport(c)
      _      <- createPendingReport(c)
    } yield expectedCompaniesToActivate = expectedCompaniesToActivate :+ ((c, None, defaultTokenCreationDate))

  def setupCaseCompanyNotifiedOnce =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(
            creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusDays(1)
          )
      )
    } yield ()

  def setupCaseCompanyNotifiedOnceLongerThanDelay =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(
            creationDate =
              OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minus(reportReminderByPostDelay).minusDays(1)
          )
      )
    } yield expectedCompaniesToActivate = expectedCompaniesToActivate :+ ((c, None, defaultTokenCreationDate))

  def setupCaseCompanyNotifiedTwice =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(
            creationDate = OffsetDateTime
              .now()
              .truncatedTo(ChronoUnit.MILLIS)
              .minus(reportReminderByPostDelay.multipliedBy(2))
              .minusDays(1)
          )
      )
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(
            creationDate =
              OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minus(reportReminderByPostDelay).minusDays(1)
          )
      )
    } yield ()

  def setupCaseCompanyNotifiedTwiceLongerThanDelay =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(
            creationDate = OffsetDateTime
              .now()
              .truncatedTo(ChronoUnit.MILLIS)
              .minus(reportReminderByPostDelay.multipliedBy(2))
              .minusDays(2)
          )
      )
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(
            creationDate = OffsetDateTime
              .now()
              .truncatedTo(ChronoUnit.MILLIS)
              .minus(reportReminderByPostDelay.multipliedBy(2))
              .minusDays(1)
          )
      )
    } yield ()

  def setupCaseCompanyNoticeRequired =
    for {
      (c, _) <- createCompanyAndToken
      _      <- createPendingReport(c)
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, POST_ACCOUNT_ACTIVATION_DOC)
          .sample
          .get
          .copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusDays(2))
      )
      _ <- eventRepository.create(
        Fixtures
          .genEventForCompany(c.id, ADMIN, ACTIVATION_DOC_REQUIRED)
          .sample
          .get
          .copy(creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS).minusDays(1))
      )
    } yield expectedCompaniesToActivate = expectedCompaniesToActivate :+ ((c, None, defaultTokenCreationDate))

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(adminUser)
        _ <- setupCaseNewCompany
        _ <- setupCaseCompanyNotifiedOnce
        _ <- setupCaseCompanyNotifiedOnceLongerThanDelay
        _ <- setupCaseCompanyNotifiedTwice
        _ <- setupCaseCompanyNoticeRequired

      } yield (),
      Duration.Inf
    )

  val (app, components) = TestApp.buildApp(
  )

}

class FetchCompaniesToActivateSpec(implicit ee: ExecutionEnv) extends BaseFetchCompaniesToActivateSpec {
  override def is = s2"""

The companies to activate endpoint should
  list companies with activation document to generate $e1
                                                    """

  def e1 = {
    val request = FakeRequest(GET, routes.CompanyController.companiesToActivate().toString)
      .withAuthCookie(adminUser.email, components.cookieAuthenticator)
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content  = contentAsJson(result).toString
    val matchers = expectedCompaniesToActivate.map(c => buildMatcherForCase(c._1, c._2, c._3))
    content must haveCompaniesToActivate(matchers)
  }

  def buildMatcherForCase(
      company: Company,
      lastNotice: Option[OffsetDateTime],
      tokenCreation: OffsetDateTime
  ): Matcher[String] =
    lastNotice match {
      case Some(lastNotice) =>
        /("company") / ("id" -> company.id.toString) and
          /("lastNotice" -> startWith(lastNotice.format(DateTimeFormatter.ISO_LOCAL_DATE))) and
          /("tokenCreation" -> startWith(tokenCreation.format(DateTimeFormatter.ISO_LOCAL_DATE)))
      case None =>
        /("company") / ("id" -> company.id.toString) and
          /("tokenCreation" -> startWith(tokenCreation.format(DateTimeFormatter.ISO_LOCAL_DATE)))
    }

  def haveCompaniesToActivate(companiesToActivate: Seq[Matcher[String]]): Matcher[String] =
    have(TraversableMatchers.exactly(companiesToActivate: _*))

}
