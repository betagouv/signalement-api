package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.ReportNotFound
import models.User
import repositories.bookmark.Bookmark
import repositories.bookmark.BookmarkRepositoryInterface
import repositories.report.ReportRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class BookmarkOrchestrator(
    reportRepository: ReportRepositoryInterface,
    bookmarkRepository: BookmarkRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  def addBookmark(reportId: UUID, user: User): Future[Unit] =
    for {
      existingBookmark <- getExistingBookmark(reportId, user)
      _ <- existingBookmark match {
        case Some(_) => Future.unit
        case None    => bookmarkRepository.create(Bookmark(reportId = reportId, userId = user.id))
      }
    } yield ()

  def removeBookmark(reportId: UUID, user: User): Future[Unit] =
    for {
      existingBookmark <- getExistingBookmark(reportId, user)
      _ <- existingBookmark match {
        case Some(_) => bookmarkRepository.deleteByIds(reportId = reportId, userId = user.id)
        case None    => Future.unit
      }
    } yield ()

  def countBookmarks(user: User): Future[Int] =
    bookmarkRepository.countForUser(user.id)

  private def getExistingBookmark(reportId: UUID, user: User): Future[Option[Bookmark]] =
    for {
      maybeReport <- reportRepository.getForWithAdditionalData(Some(user), reportId)
      report      <- maybeReport.liftTo[Future](ReportNotFound(reportId))
    } yield report.bookmark

}
