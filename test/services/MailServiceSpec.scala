package services

import cats.data.NonEmptyList
import models.company.AccessLevel
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification
import play.api.Logger
import services.emails.EmailDefinitionsPro.ProNewReportNotification
import services.emails.MailRetriesService
import services.emails.MailService
import services.emails.MailRetriesService.EmailRequest
import utils._

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class BaseMailServiceSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers
    with JsonMatchers {

  implicit val ec: ExecutionContext = ee.executionContext
  val logger: Logger                = Logger(this.getClass)

  lazy val userRepository                        = components.userRepository
  lazy val companyRepository                     = components.companyRepository
  lazy val companyAccessRepository               = components.companyAccessRepository
  lazy val companiesVisibilityOrchestrator       = components.companiesVisibilityOrchestrator
  lazy val reportNotificationBlocklistRepository = components.reportNotificationBlockedRepository

//  implicit lazy val frontRoute = components.frontRoute
  lazy val mockMailRetriesService = mock[MailRetriesService]

  val proWithAccessToHeadOffice = Fixtures.genProUser.sample.get
  val proWithAccessToSubsidiary = Fixtures.genProUser.sample.get

  val headOfficeCompany = Fixtures.genCompany.sample.get.copy(isHeadOffice = true)
  val subsidiaryCompany = Fixtures.genCompany.sample.get.copy(
    siret = Fixtures.genSiret(Some(SIREN.fromSIRET(headOfficeCompany.siret))).sample.get
  )
  val unrelatedCompany = Fixtures.genCompany.sample.get

  val reportForSubsidiary = Fixtures.genReportForCompany(subsidiaryCompany).sample.get
  val reportForUnrelated  = Fixtures.genReportForCompany(unrelatedCompany).sample.get

  override def setupData() =
    Await.result(
      for {
        _ <- userRepository.create(proWithAccessToHeadOffice)
        _ <- userRepository.create(proWithAccessToSubsidiary)

        _ <- companyRepository.getOrCreate(headOfficeCompany.siret, headOfficeCompany)
        _ <- companyRepository.getOrCreate(subsidiaryCompany.siret, subsidiaryCompany)
        _ <- companyRepository.getOrCreate(unrelatedCompany.siret, unrelatedCompany)

        _ <- companyAccessRepository.createAccess(
          headOfficeCompany.id,
          proWithAccessToHeadOffice.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createAccess(
          subsidiaryCompany.id,
          proWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )
        _ <- companyAccessRepository.createAccess(
          unrelatedCompany.id,
          proWithAccessToSubsidiary.id,
          AccessLevel.MEMBER
        )

      } yield (),
      Duration.Inf
    )

  val (app, components) = TestApp.buildApp(
  )

  protected def checkRecipients(expectedRecipients: Seq[EmailAddress]) =
    if (expectedRecipients.isEmpty) {
      there was no(mockMailRetriesService).sendEmailWithRetries(
        any[EmailRequest]
      )
    } else {
      there was one(mockMailRetriesService).sendEmailWithRetries(
        argThat((emailRequest: EmailRequest) =>
          emailRequest.recipients.sortBy(_.value).toList == expectedRecipients.sortBy(_.value)
        )
      )
    }
}

class MailServiceSpecNoBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""Email must be sent to admin and admin of head office $e1"""
  def e1 = {

    val mailService = new MailService(
      mockMailRetriesService,
      emailConfiguration = emailConfiguration,
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(
      components.frontRoute,
      executionContext
    )

    Await.result(
      mailService.send(
        ProNewReportNotification.Email(
          NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )
    Thread.sleep(100)
    checkRecipients(Seq(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email))

  }
}

class MailServiceSpecSomeBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""Email must be sent only to the user that didn't block the notifications $e1"""

  def e1 = {

    val mailService = new MailService(
      mockMailRetriesService,
      emailConfiguration = emailConfiguration,
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(
      components.frontRoute,
      executionContext
    )

    Await.result(
      reportNotificationBlocklistRepository
        .create(proWithAccessToSubsidiary.id, Seq(reportForSubsidiary.companyId.get)),
      Duration.Inf
    )

    Await.result(
      mailService.send(
        ProNewReportNotification.Email(
          NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )

    checkRecipients(Seq(proWithAccessToHeadOffice.email))

  }
}

class MailServiceSpecAllBlock(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {
  override def is = s2"""No email must be sent since all users blocked the notifications $e1"""

  def e1 = {

    val mailService = new MailService(
      mockMailRetriesService,
      emailConfiguration = emailConfiguration,
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(
      components.frontRoute,
      executionContext
    )

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

    Await.result(
      mailService.send(
        ProNewReportNotification.Email(
          NonEmptyList.of(proWithAccessToHeadOffice.email, proWithAccessToSubsidiary.email),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )

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
      mockMailRetriesService,
      emailConfiguration = emailConfiguration.copy(outboundEmailFilterRegex = ".*".r),
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(
      components.frontRoute,
      executionContext
    )

    Await.result(
      mailService.send(
        ProNewReportNotification.Email(
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
      mockMailRetriesService,
      emailConfiguration = emailConfiguration.copy(outboundEmailFilterRegex = """beta?.gouv|@.*gouv.fr""".r),
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(
      components.frontRoute,
      executionContext
    )

    Await.result(
      mailService.send(
        ProNewReportNotification.Email(
          NonEmptyList.fromListUnsafe(nonFilteredEmails ++ filteredEmail),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )
    checkRecipients(nonFilteredEmails)

  }
}

class MailServiceSpecGroupForMaxRecipients(implicit ee: ExecutionEnv) extends BaseMailServiceSpec {

  override def is = s2"""email must be grouped to prevent exceeding recipients limit $e1"""

  def e1 = {

    val recipients = List(
      EmailAddress(s"1@email.fr"),
      EmailAddress(s"2@email.fr"),
      EmailAddress(s"3@email.fr")
    )

    val mailService = new MailService(
      mockMailRetriesService,
      emailConfiguration = emailConfiguration.copy(maxRecipientsPerEmail = 2),
      reportNotificationBlocklistRepo = components.reportNotificationBlockedRepository,
      pdfService = components.pdfService,
      attachmentService = components.attachmentService
    )(
      components.frontRoute,
      executionContext
    )

    ProNewReportNotification.Email(
      NonEmptyList.of(EmailAddress(s"1@email.fr"), EmailAddress(s"2@email.fr")),
      reportForSubsidiary
    )

    Await.result(
      mailService.send(
        ProNewReportNotification.Email(
          NonEmptyList.fromListUnsafe(recipients),
          reportForSubsidiary
        )
      ),
      Duration.Inf
    )

    there was one(mockMailRetriesService).sendEmailWithRetries(
      argThat((emailRequest: EmailRequest) =>
        emailRequest.recipients.size == 2 && emailRequest.recipients.toList.containsSlice(
          List(EmailAddress(s"1@email.fr"), EmailAddress(s"2@email.fr"))
        )
      )
    )

    there was one(mockMailRetriesService).sendEmailWithRetries(
      argThat((emailRequest: EmailRequest) =>
        emailRequest.recipients.size == 1 && emailRequest.recipients.head == EmailAddress(s"3@email.fr")
      )
    )

  }
}

//MailServiceSpecNoBlock
//MailServiceSpecSomeBlock
//MailServiceSpecAllBlock
