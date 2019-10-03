package tasks

import java.time.{LocalDate, OffsetDateTime, ZoneOffset}
import java.util.UUID

import models.UserRoles.Pro
import models._
import org.specs2.{Spec, Specification}
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mock.Mockito
import repositories._
import utils.AppSpec
import utils.Constants.ActionEvent.{ActionEventValue, CONTACT_COURRIER}
import utils.Constants.EventType.PRO
import utils.Constants.StatusPro.{StatusProValue, TRAITEMENT_EN_COURS}
import utils.Constants.{ActionEvent, StatusPro}

import scala.concurrent.duration._
import scala.concurrent.Await


class RemindOngoingReport(implicit ee: ExecutionEnv) extends Specification with AppSpec with Mockito with FutureMatchers {

  import org.specs2.matcher.MatchersImplicits._

  override def is =
    sequential ^ s2"""
         Given a pro without email                                                    ${step(setupUser(userWithoutEmail))}
         Given a report with status "TRAITEMENT_EN_COURS"                             ${step(setupReport(r1))}
         Given an event "CREATION_COURRIER" created more than 21 days                 ${step(setupEvent(r1_e1))}
         When remind task run                                                         ${step(Await.result(reminderTask.runTask(runningDateTime), 5.seconds))}
         Then an event "RELANCE" is created                                           ${eventMustHaveBeenCreatedWithAction(ActionEvent.REPONSE_PRO_SIGNALEMENT)}
         And the report status is updated to "SIGNALEMENT_INFONDE"                    ${reportMustHaveBeenUpdatedWithStatus(StatusPro.SIGNALEMENT_INFONDE)}
    """


  implicit val ec = ee.executionContext

  val runningDateTime = LocalDate.of(2019, 9, 26).atStartOfDay()

  val userEmailUuid = UUID.randomUUID()
  val userWithoutEmailUuid = UUID.randomUUID()

  val userWithoutEmail = User(userWithoutEmailUuid, "11111111111111", "", Some("123123"), None, None, Some("test"), Pro)
  val userEmail = User(userEmailUuid, "22222222222222", "", None, Some("email"), None, Some("test"), Pro)

  // report 1 avec un user sans email
  val r1_uuid = UUID.randomUUID()
  val r1_date = OffsetDateTime.of(2019, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC)
  val r1 = Report(Some(r1_uuid), "test", List.empty, List("détails test"), "company1", "addresse + id " + UUID.randomUUID().toString, // pour éviter pb contrainte sur table signalement
    None, Some("11111111111111"), Some(r1_date), "r1", "nom 1", "email 1", true, List.empty, Some(TRAITEMENT_EN_COURS), None)

  // CONTACT_COURRIER plus vieux de 21j
  val r1_e1_uuid = UUID.randomUUID()
  val r1_e1_date = OffsetDateTime.of(2019, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC)
  val r1_e1 = Event(Some(r1_e1_uuid), Some(r1_uuid), Some(userWithoutEmailUuid), Some(r1_e1_date), PRO, CONTACT_COURRIER,
    None, Some("test"))


  def eventMustHaveBeenCreatedWithAction(action: ActionEventValue) = {
    eventRepository.getEvents(r1_uuid, EventFilter(action = Some(action))).map(_.head) must eventActionMatcher(action).await
  }

  def eventActionMatcher(action: ActionEventValue): org.specs2.matcher.Matcher[Event] = { event: Event =>
    (action == event.action, s"action doesn't match ${action}")
  }

  def reportMustHaveBeenUpdatedWithStatus(status: StatusProValue) = {
    reportRepository.getReport(r1_uuid) must reportStatusProMatcher(Some(status)).await
  }

  def reportStatusProMatcher(status: Option[StatusProValue]): org.specs2.matcher.Matcher[Option[Report]] = { report: Option[Report] =>
    (status == report.map(_.statusPro), s"status doesn't match ${status}")
  }

  lazy val userRepository = injector.instanceOf[UserRepository]
  lazy val reportRepository = injector.instanceOf[ReportRepository]
  lazy val eventRepository = injector.instanceOf[EventRepository]
  lazy val reminderTask = injector.instanceOf[ReminderTask]

  def setupUser(user: User) = {
    Await.result(userRepository.create(user), 1.seconds)
  }
  def setupReport(report: Report) = {
    Await.result(reportRepository.create(report), 1.seconds)
  }
  def setupEvent(event: Event) = {
    Await.result(eventRepository.createEvent(event), 1.seconds)
  }
  override def setupData() {
  }
}
