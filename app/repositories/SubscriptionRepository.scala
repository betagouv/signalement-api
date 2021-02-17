package repositories

import java.time.{OffsetDateTime, Period}
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.{Country, EmailAddress, SIRET}

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SubscriptionRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, userRepository: UserRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  private class SubscriptionTable(tag: Tag) extends Table[Subscription](tag, "subscriptions") {

    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def userId = column[Option[UUID]]("user_id")
    def email = column[Option[EmailAddress]]("email")
    def departments = column[List[String]]("departments")
    def categories = column[List[String]]("categories")
    def tags = column[List[String]]("tags")
    def countries = column[List[Country]]("countries")
    def sirets = column[List[SIRET]]("sirets")
    def user = foreignKey("fk_subscription_user", userId, userTableQuery)(_.id)
    def frequency = column[Period]("frequency")

    type SubscriptionData = (UUID, OffsetDateTime, Option[UUID], Option[EmailAddress], List[String], List[String], List[String], List[Country], List[SIRET], Period)

    def constructSubscription: SubscriptionData => Subscription = {
      case (id, creationDate, userId, email, departments, categories, tags, countries, sirets, frequency) => {
        Subscription(
          id = id,
          creationDate = creationDate,
          userId = userId,
          email = email,
          departments = departments,
          categories = categories.map(ReportCategory.fromValue),
          tags = tags,
          countries = countries,
          sirets = sirets,
          frequency = frequency
        )
      }
    }

    def extractSubscription: PartialFunction[Subscription, SubscriptionData] = {
      case Subscription(id, creationDate, userId, email, departments, categories, tags, countries, sirets, frequency) => (id, creationDate, userId, email, departments, categories.map(_.value), tags, countries, sirets, frequency)
    }

    def * =
      (id, creationDate, userId, email, departments, categories, tags, countries, sirets, frequency) <> (constructSubscription, extractSubscription.lift)
  }

  private val subscriptionTableQuery = TableQuery[SubscriptionTable]

  private val userTableQuery = TableQuery[userRepository.UserTable]

  def create(subscription: Subscription): Future[Subscription] = db
    .run(subscriptionTableQuery += subscription)
    .map(_ => subscription)


  def get(id: UUID): Future[Option[Subscription]] = db
    .run(
      subscriptionTableQuery
      .filter(_.id === id)
      .result.headOption
    )

  def list(userId: UUID): Future[List[Subscription]] = db
    .run(
      subscriptionTableQuery
        .filter(_.userId === userId)
        .sortBy(_.creationDate.desc)
        .to[List].result
    )

  def update(subscription: Subscription): Future[Subscription] = {
    val querySubscription = for (refSubscription <- subscriptionTableQuery if refSubscription.id === subscription.id)
      yield refSubscription
    db.run(querySubscription.update(subscription))
      .map(_ => subscription)
  }

  def delete(subscriptionId: UUID): Future[Int] = db
    .run(subscriptionTableQuery.filter(_.id === subscriptionId).delete)

  def listForFrequency(frequency: Period): Future[List[(Subscription, EmailAddress)]] = db
    .run(
      subscriptionTableQuery
        .filter(_.frequency === frequency)
        .joinLeft(userTableQuery).on(_.userId === _.id)
        .map(subscription => (subscription._1, subscription._1.email.ifNull(subscription._2.map(_.email)).get))
        .to[List]
        .result
    )
}

