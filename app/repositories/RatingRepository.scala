package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.Rating
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class RatingRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, reportRepository: ReportRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  private class RatingTable(tag: Tag) extends Table[Rating](tag, "ratings") {

    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def category = column[String]("category")
    def subcategories = column[List[String]]("subcategories")
    def positive = column[Boolean]("positive")

    type RatingData = (UUID, OffsetDateTime, String, List[String], Boolean)

    def constructRating: RatingData => Rating = {

      case (id, creationDate, category, subcategories, positive) => {
        Rating(Some(id), Some(creationDate), category, subcategories, positive)
      }
    }

    def extractRating: PartialFunction[Rating, RatingData] = {
      case Rating(id, creationDate, category, subcategories, positive) => (id.get, creationDate.get, category, subcategories, positive)
    }

    def * =
      (id, creationDate, category, subcategories, positive) <> (constructRating, extractRating.lift)
  }

  private val ratingTableQuery = TableQuery[RatingTable]

  def createRating(rating: Rating): Future[Rating] = db
    .run(ratingTableQuery += rating)
    .map(_ => rating)

}

