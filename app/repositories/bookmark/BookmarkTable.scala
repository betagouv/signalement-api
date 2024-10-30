package repositories.bookmark

import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape
import slick.lifted.Tag

import java.util.UUID

class BookmarkTable(tag: Tag) extends DatabaseTable[Bookmark](tag, "bookmarks") {

  def reportId = column[UUID]("report_id")
  def userId   = column[UUID]("user_id")

  type BookmarkData = (
      UUID,
      UUID
  )

  def constructBookmark: BookmarkData => Bookmark = { case (reportId, userId) =>
    Bookmark(reportId, userId)
  }

  def extractBookmark: PartialFunction[Bookmark, BookmarkData] = { case Bookmark(reportId, userId) =>
    (reportId, userId)
  }

  override def * : ProvenShape[Bookmark] =
    (
      reportId,
      userId
    ) <> (constructBookmark, extractBookmark.lift)
}

object BookmarkTable {
  val table = TableQuery[BookmarkTable]
}
