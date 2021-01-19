package controllers

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import com.google.inject.AbstractModule
import com.mohiva.play.silhouette.api.{Environment, LoginInfo}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import models._
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.{FutureMatchers, JsonMatchers, Matcher, TraversableMatchers}
import org.specs2.mutable.Specification
import play.api.Logger
import play.api.test.Helpers._
import play.api.test._
import repositories.{AccessTokenRepository, CompanyRepository, EventRepository, _}
import utils.Constants.{ActionEvent, EventType}
import utils.silhouette.auth.AuthEnv
import utils.{AppSpec, Fixtures}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class BaseCompanyControllerSpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers with JsonMatchers {

  implicit val ec = ee.executionContext
  val logger: Logger = Logger(this.getClass)

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val accessTokenRepository = injector.instanceOf[AccessTokenRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]

  val tokenDuration = java.time.Period.parse("P60D")
  val reportReminderByPostDelay = java.time.Period.parse("P28D")
  val tokenCreation = OffsetDateTime.now.minusMonths(1)
  val expiredLastNotice = OffsetDateTime.now.minus(reportReminderByPostDelay).minusDays(1)

  val adminUser = Fixtures.genAdminUser.sample.get
  val company = Fixtures.genCompany.sample.get
  val companyWithSentDoc = Fixtures.genCompany.sample.get
  val companyWithExpiredSentDoc = Fixtures.genCompany.sample.get
  val companyWithRequiredDoc = Fixtures.genCompany.sample.get

  override def setupData = {
    Await.result(for {
      _ <- userRepository.create(adminUser)

      _ <- companyRepository.getOrCreate(company.siret, company)
      _ <- accessTokenRepository.createToken(TokenKind.COMPANY_INIT, f"${Random.nextInt(1000000)}%06d", Some(tokenDuration), Some(company), Some(AccessLevel.ADMIN), None, tokenCreation)

      _ <- companyRepository.getOrCreate(companyWithSentDoc.siret, companyWithSentDoc)
      _ <- accessTokenRepository.createToken(TokenKind.COMPANY_INIT, f"${Random.nextInt(1000000)}%06d", Some(tokenDuration), Some(companyWithSentDoc), Some(AccessLevel.ADMIN), None, tokenCreation)
      _ <- eventRepository.createEvent(Fixtures.genEventForCompany(companyWithSentDoc.id, EventType.ADMIN, ActionEvent.POST_ACCOUNT_ACTIVATION_DOC).sample.get.copy(creationDate = Some(OffsetDateTime.now.minusDays(1))))

      _ <- companyRepository.getOrCreate(companyWithExpiredSentDoc.siret, companyWithExpiredSentDoc)
      _ <- accessTokenRepository.createToken(TokenKind.COMPANY_INIT, f"${Random.nextInt(1000000)}%06d", Some(tokenDuration), Some(companyWithExpiredSentDoc), Some(AccessLevel.ADMIN), None, tokenCreation)
      _ <- eventRepository.createEvent(Fixtures.genEventForCompany(companyWithExpiredSentDoc.id, EventType.ADMIN, ActionEvent.POST_ACCOUNT_ACTIVATION_DOC).sample.get.copy(creationDate = Some(expiredLastNotice)))

      _ <- companyRepository.getOrCreate(companyWithRequiredDoc.siret, companyWithRequiredDoc)
      _ <- accessTokenRepository.createToken(TokenKind.COMPANY_INIT, f"${Random.nextInt(1000000)}%06d", Some(tokenDuration), Some(companyWithRequiredDoc), Some(AccessLevel.ADMIN), None, tokenCreation)
      _ <- eventRepository.createEvent(Fixtures.genEventForCompany(companyWithRequiredDoc.id, EventType.ADMIN, ActionEvent.POST_ACCOUNT_ACTIVATION_DOC).sample.get.copy(creationDate = Some(OffsetDateTime.now.minusDays(2))))
      _ <- eventRepository.createEvent(Fixtures.genEventForCompany(companyWithRequiredDoc.id, EventType.ADMIN, ActionEvent.ACTIVATION_DOC_REQUIRED).sample.get.copy(creationDate = Some(OffsetDateTime.now.minusDays(1))))
    } yield Unit,
    Duration.Inf)
  }
  override def configureFakeModule(): AbstractModule = {
    new FakeModule
  }

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](Seq(adminUser).map(
    user => loginInfo(user) -> user
  ))

  class FakeModule extends AppFakeModule {
    override def configure() = {
      super.configure
      bind[Environment[AuthEnv]].toInstance(env)
    }
  }
}

class FetchCompaniesToActivateSpec(implicit ee: ExecutionEnv) extends BaseCompanyControllerSpec { override def is = s2"""

The companies to activate endpoint should
  list companies with activation document to generate $e1
                                                    """

  def e1 = {
    val request = FakeRequest(GET, routes.CompanyController.companiesToActivate().toString)
                  .withAuthenticator[AuthEnv](loginInfo(adminUser))
    val result = route(app, request).get
    status(result) must beEqualTo(OK)
    val content = contentAsJson(result).toString
    content must haveCompaniesToActivate(
      aCompanyToActivate(company, None, tokenCreation),
      aCompanyToActivate(companyWithExpiredSentDoc, Some(expiredLastNotice), tokenCreation),
      aCompanyToActivate(companyWithRequiredDoc, None, tokenCreation),
    )
  }

  def aCompanyToActivate(company: Company, lastNotice: Option[OffsetDateTime], tokenCreation: OffsetDateTime): Matcher[String] = {
    lastNotice match {
      case Some(lastNotice) =>
        /("company") /("id" -> company.id.toString) and
          /("lastNotice" -> startWith(lastNotice.format(DateTimeFormatter.ISO_LOCAL_DATE))) and
          /("tokenCreation" -> startWith(tokenCreation.format(DateTimeFormatter.ISO_LOCAL_DATE)))
      case None =>
        /("company") /("id" -> company.id.toString) and
          /("tokenCreation" -> startWith(tokenCreation.format(DateTimeFormatter.ISO_LOCAL_DATE)))
    }
  }

  def haveCompaniesToActivate(companiesToActivate : Matcher[String]*): Matcher[String] =
    have(TraversableMatchers.exactly(companiesToActivate:_*))

}

