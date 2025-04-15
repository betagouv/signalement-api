package repositories.subscription

import models.Subscription
import models.report.ReportCategory
import models.report.ReportTag
import repositories.DatabaseTable
import utils.Country
import utils.EmailAddress
import utils.SIRET
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import repositories.report.ReportColumnType.ReportTagListColumnType

class SubscriptionTable(tag: Tag) extends DatabaseTable[Subscription](tag, "subscriptions") {

  def creationDate = column[OffsetDateTime]("creation_date")
  def userId       = column[Option[UUID]]("user_id")
  def email        = column[Option[EmailAddress]]("email")
  def departments  = column[List[String]]("departments")
  def categories   = column[List[String]]("categories")
  def withTags     = column[List[ReportTag]]("with_tags")
  def withoutTags  = column[List[ReportTag]]("without_tags")
  def countries    = column[List[Country]]("countries")
  def sirets       = column[List[SIRET]]("sirets")
  def websites     = column[List[String]]("websites")
  def phones       = column[List[String]]("phones")
  def frequency    = column[Period]("frequency")

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
      List[String],
      List[String],
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
          websites,
          phones,
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
        websites = websites,
        phones = phones,
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
          websites,
          phones,
          frequency
        ) =>
      (
        id,
        creationDate,
        userId,
        email,
        departments,
        categories.map(_.entryName),
        withTags,
        withoutTags,
        countries,
        sirets,
        websites,
        phones,
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
      websites,
      phones,
      frequency
    ) <> (constructSubscription, extractSubscription.lift)
}

object SubscriptionTable {
  val table = TableQuery[SubscriptionTable]
}
