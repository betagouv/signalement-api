package services

import cats.data.NonEmptyList
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.test._
import models._
import models.company.AccessLevel
import models.report.Report
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import play.api.Logger
import services.Email.ProNewReportNotification
import services.MailRetriesService.EmailRequest
import utils._
import utils.silhouette.auth.AuthEnv

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

class BaseMailServiceSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val ec = ee.executionContext
  val logger: Logger = Logger(this.getClass)

  lazy val userRepository = components.userRepository
  lazy val companyRepository = components.companyRepository
  lazy val companyAccessRepository = components.companyAccessRepository
  lazy val companiesVisibilityOrchestrator = components.companiesVisibilityOrchestrator
  lazy val reportNotificationBlocklistRepository = components.reportNotificationBlockedRepository

//  implicit lazy val frontRoute = components.frontRoute
  lazy val mailRetriesService = mock[MailRetriesService]
  lazy val mailService = components.mailService

  val proWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val proWithAccessToSubsidiary = Fixtures.genProUser.sample.get

  val headOfficeCompany = Fixtures.genCompany.sample.get.copy(isHeadOffice = true)
  val subsidiaryCompany = Fixtures.genCompany.sample.get.copy(
    siret = Fixtures.genSiret(Some(SIREN(headOfficeCompany.siret))).sample.get
  )
  val unrelatedCompany = Fixtures.genCompany.sample.get

  val reportForSubsidiary = Fixtures.genReportForCompany(subsidiaryCompany).sample.get
  val reportForUnrelated = Fixtures.genReportForCompany(unrelatedCompany).sample.get

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(proWithAccessToHeadOffice)
        _ <- userRepository.create(proWithAccessToSubsidiary)

        _ <- companyRepository.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
        _ <- companyRepository.getOrCreate(subsidiaryCompany.siret, subsidiaryCompany)
        _ <- companyRepository.getOrCreate(unrelatedCompany.siret, unrelatedCompany)

        _ <- companyAccessRepository.createUserAccess(
          headOfficeCompany.id,
          proWithAccessToHeadOffice.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createUserAccess(
          subsidiaryCompany.id,
          proWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createUserAccess(
          unrelatedCompany.id,
          proWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )

      } yield (),
      Duration.Inf
    )

  def loginInfo(user: User) = LoginInfo(CredentialsProvider.ID, user.email.value)

  implicit val env = new FakeEnvironment[AuthEnv](
    Seq(proWithAccessToHeadOffice, proWithAccessToSubsidiary).map(user => loginInfo(user) -> user)
  )

  val (app, components) = TestApp.buildApp(
    Some(
      env
    )
  )

  protected def sendEmail(emails: NonEmptyList[EmailAddress], report: Report) =
    Await.result(
      mailService.send(
        ProNewReportNotification(
          emails,
          report
        )
      ),
      Duration.Inf
    )

  protected def checkRecipients(expectedRecipients: Seq[EmailAddress]) =
    if (expectedRecipients.isEmpty) {
      there was no(mailRetriesService).sendEmailWithRetries(
        any[EmailRequest]
      )
    } else {
      there was one(mailRetriesService).sendEmailWithRetries(
        argThat((emailRequest: EmailRequest) =>
          emailRequest.recipients.sortBy(_.value).toList == expectedRecipients.sortBy(_.value)
        )
      )
    }
}

class MailServiceSpecNoBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""Email must be sent to admin and admin of head office $e1"""
  def e1 = {
    sendEmail(NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email), reportForSubsidiary)
    Thread.sleep(100)
    checkRecipients(Seq(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email))
  }
}

class MailServiceSpecSomeBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""Email must be sent only to the user that didn't block the notifications $e1"""

  def e1 = {
    Await.result(
      reportNotificationBlocklistRepository
        .create(proWithAccessToSubsidiary.id, Seq(reportForSubsidiary.companyId.get)),
      Duration.Inf
    )
    sendEmail(NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email), reportForSubsidiary)
    checkRecipients(Seq(proWithAccessToHeadOffice.email))
  }
}

class MailServiceSpecAllBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""No email must be sent since all users blocked the notifications $e1"""

  def e1 = {
    Await.result(
      Future.sequence(
        Seq(
          reportNotificationBlocklistRepository
            .create(proWithAccessToSubsidiary.id, Seq(reportForSubsidiary.companyId.get)),
          reportNotificationBlocklistRepository
            .create(proWithAccessToHeadOffice.id, Seq(reportForSubsidiary.companyId.get))
        )
      ),
      Duration.Inf
    )

    sendEmail(NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email), reportForSubsidiary)
    checkRecipients(Seq())
  }
}

class MailServiceSpecNotFilteredEmail(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {

  override def is = s2"""No email must be filtered $e1"""

  def e1 = {

    val nonFilteredEmails = List(
      EmailAddress(s"${UUID.randomUUID().toString}@betagouv.fr"),
      EmailAddress(s"${UUID.randomUUID().toString}@beta.gouv.fr"),
      EmailAddress(s"${UUID.randomUUID().toString}beta.gouv@gmail.com"),
      EmailAddress(s"${UUID.randomUUID().toString}@dgccrf.finances.gouv.fr"),
      EmailAddress(s"${UUID.randomUUID().toString}@${UUID.randomUUID().toString}.gouv.fr")
    )

    val filteredEmail = List(
      EmailAddress(s"${UUID.randomUUID().toString}@gmail.com"),
      EmailAddress(s"${UUID.randomUUID().toString}@outlook.com")
    )

    val mailService = new MailService(
      mailRetriesService,
      emailConfiguration = emailConfiguration.copy(outboundEmailFilterRegex = ".*".r),
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(components.frontRoute, executionContext)

    Await.result(
      mailService.send(
        ProNewReportNotification(
          NonEmptyList.fromListUnsafe(nonFilteredEmails ++ filteredEmail),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )

    checkRecipients(nonFilteredEmails ++ filteredEmail)
  }
}

class MailServiceSpecFilteredEmail(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {

  override def is = s2"""email must be filtered $e1"""

  def e1 = {

    val nonFilteredEmails = List(
      EmailAddress(s"${UUID.randomUUID().toString}@betagouv.fr"),
      EmailAddress(s"${UUID.randomUUID().toString}@beta.gouv.fr"),
      EmailAddress(s"${UUID.randomUUID().toString}@beta.gouv@gmail.com"),
      EmailAddress(s"${UUID.randomUUID().toString}@dgccrf.finances.gouv.fr"),
      EmailAddress(s"${UUID.randomUUID().toString}@${UUID.randomUUID().toString}.gouv.fr")
    )

    val filteredEmail = List(
      EmailAddress(s"${UUID.randomUUID().toString}@gmail.com"),
      EmailAddress(s"${UUID.randomUUID().toString}@outlook.com")
    )

    val mailService = new MailService(
      mailRetriesService,
      emailConfiguration = emailConfiguration.copy(outboundEmailFilterRegex = """beta?.gouv|@.*gouv.fr""".r),
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(components.frontRoute, executionContext)

    Await.result(
      mailService.send(
        ProNewReportNotification(
          NonEmptyList.fromListUnsafe(nonFilteredEmails ++ filteredEmail),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )
    checkRecipients(nonFilteredEmails)

  }
}

//MailServiceSpecNoBlock
//MailServiceSpecSomeBlock
//MailServiceSpecAllBlock
