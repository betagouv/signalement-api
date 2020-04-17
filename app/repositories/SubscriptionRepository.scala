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
    def departments = column[List[String]]("departments")
    def categories = column[List[String]]("categories")
    def user = foreignKey("fk_subscription_user", userId, userTableQuery)(_.id)

    type SubscriptionData = (UUID, Option[UUID], Option[EmailAddress], List[String], List[String])

    def constructSubscription: SubscriptionData => Subscription = {
      case (id, userId, email, departments, categories) => {
        Subscription(id, userId, email, departments, categories.map(ReportCategory.fromValue(_)))
      }
    }

    def extractSubscription: PartialFunction[Subscription, SubscriptionData] = {
      case Subscription(id, userId, email, departments, categories) => (id, userId, email, departments, categories.map(_.value))
    }

    def * =
      (id, userId, email, departments, categories) <> (constructSubscription, extractSubscription.lift)
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

  def delete(subscriptionId: UUID): Future[Int] = db
    .run(subscriptionTableQuery.filter(_.id === subscriptionId).delete)

  def listSubscribeUserMails(department: String, category: Option[ReportCategory]): Future[List[EmailAddress]] = db
    .run(
      subscriptionTableQuery
        .filter(subscription => department.bind === subscription.departments.any)
        .filterOpt(category) {
          case (table, category) => category.value.bind === table.categories.any
        }
        .filterIf(!category.isDefined) {
          case table => 0.bind === table.categories.length()
        }
        .joinLeft(userTableQuery).on(_.userId === _.id)
        .map(subscription => subscription._1.email.ifNull(subscription._2.map(_.email)))
        .to[List]
        .result
        .map(_.flatten)
    )
}

