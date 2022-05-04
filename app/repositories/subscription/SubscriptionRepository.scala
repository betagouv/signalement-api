package repositories.subscription

import models.Subscription
import play.api.db.slick.DatabaseConfigProvider
import repositories.PostgresProfile
import repositories.user.UserTable
import slick.jdbc.JdbcProfile
import utils.EmailAddress
import utils.EmailAddress.EmailColumnType

import java.time.Period
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class SubscriptionRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  def create(subscription: Subscription): Future[Subscription] = db
    .run(SubscriptionTable.table += subscription)
    .map(_ => subscription)

  def get(id: UUID): Future[Option[Subscription]] = db
    .run(
      SubscriptionTable.table
        .filter(_.id === id)
        .result
        .headOption
    )

  def update(subscription: Subscription): Future[Subscription] = {
    val querySubscription =
      for (refSubscription <- SubscriptionTable.table if refSubscription.id === subscription.id)
        yield refSubscription
    db.run(querySubscription.update(subscription))
      .map(_ => subscription)
  }

  def delete(subscriptionId: UUID): Future[Int] = db
    .run(SubscriptionTable.table.filter(_.id === subscriptionId).delete)

  def list(userId: UUID): Future[List[Subscription]] = db
    .run(
      SubscriptionTable.table
        .filter(_.userId === userId)
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )

  def listForFrequency(frequency: Period): Future[List[(Subscription, EmailAddress)]] = db
    .run(
      SubscriptionTable.table
        .filter(_.frequency === frequency)
        .joinLeft(UserTable.table)
        .on(_.userId === _.id)
        .map(s => (s._1, s._1.email.ifNull(s._2.map(_.email)).get))
        .to[List]
        .result
    )

  def getDirectionDepartementaleEmail(department: String): Future[Seq[EmailAddress]] =
    db.run(
      SubscriptionTable.table
        .filter(_.email.isDefined)
        .filter(_.userId.isEmpty)
        .filter(x => x.departments @> List(department))
        .filter(x => x.email.map(_.asColumnOf[String]) like s"dd%")
        .result
    ).map(_.map(_.email.get).distinct)
}
