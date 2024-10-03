package tasks.report

import models.AsyncFileKind.ReportedPhones
import models.AsyncFileKind.ReportedWebsites
import models.AsyncFileKind.Reports
import models.report.Report
import models.AsyncFile
import models.AsyncFileKind
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import play.api.mvc.Results
import play.api.test.WithApplication
import repositories.event.EventFilter
import utils.Constants.ActionEvent.ActionEventValue
import utils.AppSpec
import utils.Fixtures
import utils.S3ServiceMock
import utils.TestApp

import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration

class OldReportExportDeletionTaskSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with Results
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  lazy val asyncFileRepository = components.asyncFileRepository
  lazy val userRepository      = components.userRepository
  val queue                    = new ConcurrentLinkedQueue[String]()

  val fileDeletionTask = new OldReportExportDeletionTask(
    components.actorSystem,
    components.asyncFileRepository,
    new S3ServiceMock(queue),
    taskConfiguration,
    components.taskRepository
  )(components.executionContext, components.materializer)
  lazy val eventRepository = components.eventRepository

  val creationDate    = OffsetDateTime.parse("2020-01-01T00:00:00Z")
  val taskRunDate     = OffsetDateTime.parse("2020-06-01T00:00:00Z")
  val dateInThePast   = taskRunDate.minusDays(5)
  val dateInTheFuture = taskRunDate.plusDays(5)

  def genUser() =
    Fixtures.genUser.sample.get

  def genAsyncFile(userId: UUID, creationDate: OffsetDateTime, kind: AsyncFileKind) =
    AsyncFile(
      UUID.randomUUID(),
      userId,
      creationDate = creationDate,
      Some(UUID.randomUUID().toString),
      kind,
      Some(UUID.randomUUID().toString)
    )

  def hasEvent(report: Report, action: ActionEventValue): Future[Boolean] =
    eventRepository
      .getEvents(report.id, EventFilter(action = Some(action)))
      .map(_.nonEmpty)

  "OldReportExportDeletionTask should delete old files" >> {

    val user                         = genUser()
    val recentReportedWebsitesExport = genAsyncFile(user.id, creationDate = OffsetDateTime.now(), ReportedWebsites)
    val oldReportedWebsitesExport =
      genAsyncFile(user.id, creationDate = OffsetDateTime.now().minusYears(3), ReportedWebsites)

    val recentReportedPhonesExport = genAsyncFile(user.id, creationDate = OffsetDateTime.now(), ReportedPhones)
    val oldReportedPhonesExport =
      genAsyncFile(user.id, creationDate = OffsetDateTime.now().minusYears(3), ReportedPhones)

    val recentReportsExport = genAsyncFile(user.id, creationDate = OffsetDateTime.now(), Reports)
    val oldReportsExport    = genAsyncFile(user.id, creationDate = OffsetDateTime.now().minusYears(3), Reports)

    def setup(): Future[Unit] =
      for {
        _ <- userRepository.create(user)
        _ = queue.add(recentReportedWebsitesExport.storageFilename.get)
        _ = queue.add(oldReportedWebsitesExport.storageFilename.get)
        _ = queue.add(recentReportedPhonesExport.storageFilename.get)
        _ = queue.add(oldReportedPhonesExport.storageFilename.get)
        _ = queue.add(recentReportsExport.storageFilename.get)
        _ = queue.add(oldReportsExport.storageFilename.get)

        _ <- asyncFileRepository.create(recentReportedWebsitesExport)
        _ <- asyncFileRepository.create(oldReportedWebsitesExport)
        _ <- asyncFileRepository.create(recentReportedPhonesExport)
        _ <- asyncFileRepository.create(oldReportedPhonesExport)
        _ <- asyncFileRepository.create(recentReportsExport)
        _ <- asyncFileRepository.create(oldReportsExport)

        _ <- userRepository.get(user.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(recentReportedWebsitesExport.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(oldReportedWebsitesExport.id) map (_.isDefined must beTrue)
      } yield ()

    def check(): Future[Unit] =
      for {
        _ <- userRepository.get(user.id) map (_.isDefined must beTrue)

        _ <- asyncFileRepository.get(recentReportedWebsitesExport.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(oldReportedWebsitesExport.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(recentReportedPhonesExport.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(oldReportedPhonesExport.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(recentReportsExport.id) map (_.isDefined must beTrue)
        _ <- asyncFileRepository.get(oldReportsExport.id) map (_.isDefined must beTrue)

        _ = queue.contains(recentReportedWebsitesExport.storageFilename.get) must beTrue
        _ = queue.contains(oldReportedWebsitesExport.storageFilename.get) must beFalse
        _ = queue.contains(recentReportedPhonesExport.storageFilename.get) must beTrue
        _ = queue.contains(oldReportedPhonesExport.storageFilename.get) must beFalse
        _ = queue.contains(recentReportsExport.storageFilename.get) must beTrue
        _ = queue.contains(oldReportsExport.storageFilename.get) must beFalse

      } yield ()

    new WithApplication(app) {
      Await.result(
        for {
          _ <- setup()
          _ <- fileDeletionTask.runTask()
          _ <- check()
        } yield (),
        Duration.Inf
      )
    }
  }
}
