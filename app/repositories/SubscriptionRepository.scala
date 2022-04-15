package repositories

import models._
import models.report.ReportCategory
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.Country
import utils.EmailAddress
import utils.SIRET
import models.report.ReportTag

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import repositories.report.ReportColumnType._
import repositories.user.UserTable

@Singleton
class SubscriptionRepository @Inject() (dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

//  implicit lazy val reportTagMapper = mappedColumnTypeForEnum(ReportTag)

  private class SubscriptionTable(tag: Tag) extends Table[Subscription](tag, "subscriptions") {

    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def userId = column[Option[UUID]]("user_id")
    def email = column[Option[EmailAddress]]("email")
    def departments = column[List[String]]("departments")
    def categories = column[List[String]]("categories")
    def withTags = column[List[ReportTag]]("with_tags")
    def withoutTags = column[List[ReportTag]]("without_tags")
    def countries = column[List[Country]]("countries")
    def sirets = column[List[SIRET]]("sirets")
    def frequency = column[Period]("frequency")

    type SubscriptionData = (
        UUID,
        OffsetDateTime,
        Option[UUID],
        Option[EmailAddress],
        List[String],
        List[String],
        List[ReportTag],
        List[ReportTag],
        List[Country],
        List[SIRET],
        Period
    )

    def constructSubscription: SubscriptionData => Subscription = {
      case (
            id,
            creationDate,
            userId,
            email,
            departments,
            categories,
            withTags,
            withoutTags,
            countries,
            sirets,
            frequency
          ) =>
        Subscription(
          id = id,
          creationDate = creationDate,
          userId = userId,
          email = email,
          departments = departments,
          categories = categories.map(ReportCategory.fromValue),
          withTags = withTags,
          withoutTags = withoutTags,
          countries = countries,
          sirets = sirets,
          frequency = frequency
        )
    }

    def extractSubscription: PartialFunction[Subscription, SubscriptionData] = {
      case Subscription(
            id,
            creationDate,
            userId,
            email,
            departments,
            categories,
            withTags,
            withoutTags,
            countries,
            sirets,
            frequency
          ) =>
        (
          id,
          creationDate,
          userId,
          email,
          departments,
          categories.map(_.value),
          withTags,
          withoutTags,
          countries,
          sirets,
          frequency
        )
    }

    def * =
      (
        id,
        creationDate,
        userId,
        email,
        departments,
        categories,
        withTags,
        withoutTags,
        countries,
        sirets,
        frequency
      ) <> (constructSubscription, extractSubscription.lift)
  }

  private val subscriptionTableQuery = TableQuery[SubscriptionTable]

  def create(subscription: Subscription): Future[Subscription] = db
    .run(subscriptionTableQuery += subscription)
    .map(_ => subscription)

  def get(id: UUID): Future[Option[Subscription]] = db
    .run(
      subscriptionTableQuery
        .filter(_.id === id)
        .result
        .headOption
    )

  def list(userId: UUID): Future[List[Subscription]] = db
    .run(
      subscriptionTableQuery
        .filter(_.userId === userId)
        .sortBy(_.creationDate.desc)
        .to[List]
        .result
    )

  def update(subscription: Subscription): Future[Subscription] = {
    val querySubscription =
      for (refSubscription <- subscriptionTableQuery if refSubscription.id === subscription.id)
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
        .joinLeft(UserTable.table)
        .on(_.userId === _.id)
        .map(s => (s._1, s._1.email.ifNull(s._2.map(_.email)).get))
        .to[List]
        .result
    )

  def getDirectionDepartementaleEmail(department: String): Future[Seq[EmailAddress]] =
    db.run(
      subscriptionTableQuery
        .filter(_.email.isDefined)
        .filter(_.userId.isEmpty)
        .filter(x => x.departments @> List(department))
        .filter(x => x.email.map(_.asColumnOf[String]) like s"dd%")
        .result
    ).map(_.map(_.email.get).distinct)
}
