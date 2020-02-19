package repositories

import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.EmailAddress

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class SubscriptionRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, userRepository: UserRepository)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  private class SubscriptionTable(tag: Tag) extends Table[Subscription](tag, "subscriptions") {

    def id = column[UUID]("id", O.PrimaryKey)
    def userId = column[Option[UUID]]("user_id")
    def email = column[Option[EmailAddress]]("email")
    def category = column[String]("category")
    def values = column[List[String]]("values")
    def user = foreignKey("fk_subscription_user", userId, userTableQuery)(_.id)

    type SubscriptionData = (UUID, Option[UUID], Option[EmailAddress], String, List[String])

    def constructSubscription: SubscriptionData => Subscription = {
      case (id, userId, email, category, values) => {
        Subscription(Some(id), userId, email, category, values)
      }
    }

    def extractSubscription: PartialFunction[Subscription, SubscriptionData] = {
      case Subscription(id, userId, email, category, values) => (id.get, userId, email, category, values)
    }

    def * =
      (id, userId, email, category, values) <> (constructSubscription, extractSubscription.lift)
  }

  private val subscriptionTableQuery = TableQuery[SubscriptionTable]

  private val userTableQuery = TableQuery[userRepository.UserTable]

  def create(subscription: Subscription): Future[Subscription] = db
    .run(subscriptionTableQuery += subscription)
    .map(_ => subscription)

  def list(userId: UUID): Future[List[Subscription]] = db
    .run(
      subscriptionTableQuery
        .filter(_.userId === userId)
        .to[List].result
    )

  def update(subscription: Subscription): Future[Subscription] = {
    val querySubscription = for (refSubscription <- subscriptionTableQuery if refSubscription.id === subscription.id)
      yield refSubscription
    db.run(querySubscription.update(subscription))
      .map(_ => subscription)
  }

  def listSubscribeUserMailsForDepartment(code: String): Future[List[EmailAddress]] = db
    .run(
      subscriptionTableQuery
        .filter(subscription => code.bind === subscription.values.any)
        .joinLeft(userTableQuery).on(_.userId === _.id)
        .map(subscription => subscription._1.email.ifNull(subscription._2.map(_.email)))
        .filter(_.isDefined)
        .map(_.get)
        .to[List]
        .result
    )
}

