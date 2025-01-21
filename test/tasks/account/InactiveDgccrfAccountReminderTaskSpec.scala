package tasks.account

import loader.SignalConsoComponents
import models.event.Event
import org.mockito.Mockito.when
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import play.api.Application
import play.api.ApplicationLoader
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.Results
import repositories.event.EventRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.emails.MailRetriesService.EmailRequest
import services.emails.MailRetriesService
import utils.AppSpec
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.concurrent.Future

class InactiveDgccrfAccountReminderTaskSpec(implicit ee: ExecutionEnv)
    extends org.specs2.mutable.Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val mockMailRetriesService = mock[MailRetriesService]
  val mockEventRepository    = mock[EventRepositoryInterface]
  val mockUserRepository     = mock[UserRepositoryInterface]

  class FakeApplicationLoader extends ApplicationLoader {
    var components: SignalConsoComponents = _

    override def load(context: ApplicationLoader.Context): Application = {
      components = new SignalConsoComponents(context) {

        override lazy val mailRetriesService: MailRetriesService = mockMailRetriesService

        override val eventRepository: EventRepositoryInterface = mockEventRepository

        override val userRepository: UserRepositoryInterface = mockUserRepository

        override def configuration: Configuration = Configuration(
          "slick.dbs.default.db.connectionPool" -> "disabled",
          "play.mailer.mock"                    -> true
        ).withFallback(
          super.configuration
        )

      }
      components.application
    }

  }

  val appLoader        = new FakeApplicationLoader()
  val app: Application = TestApp.buildApp(appLoader)
  val mailService      = appLoader.components.mailService

  "InactiveDgccrfAccountReminderTask" should {

    "send send emails and create events" in {

      val now            = OffsetDateTime.now()
      val firstReminder  = now.minusMonths(6)
      val secondReminder = now.minusMonths(9)
      val expiration     = now.minusMonths(12)

      val firstMailToSendUser = Fixtures.genDgccrfUser.sample.get

      val event = Event(
        UUID.randomUUID(),
        None,
        None,
        Some(firstMailToSendUser.id),
        OffsetDateTime.now(),
        EventType.DGCCRF,
        ActionEvent.EMAIL_INACTIVE_AGENT_ACCOUNT,
        Json.obj()
      )

      when(mockUserRepository.listInactiveAgentsWithSentEmailCount(firstReminder, expiration))
        .thenReturn(Future.successful(List(firstMailToSendUser -> None)))
      when(mockUserRepository.listInactiveAgentsWithSentEmailCount(secondReminder, expiration))
        .thenReturn(Future.successful(List.empty))
      when(mockEventRepository.create(any[Event]())).thenReturn(Future.successful(event))

      val test = new InactiveDgccrfAccountReminderTask(mockUserRepository, mockEventRepository, mailService)

      test.sendReminderEmail(firstReminder, secondReminder, expiration, Period.ofYears(1)) must beEqualTo(()).await
      there was one(mockUserRepository).listInactiveAgentsWithSentEmailCount(firstReminder, expiration)
      there was one(mockUserRepository).listInactiveAgentsWithSentEmailCount(secondReminder, expiration)
      there was one(mockEventRepository).create(any[Event]())
      there was one(mockMailRetriesService).sendEmailWithRetries(any[EmailRequest]())
    }

  }

}
