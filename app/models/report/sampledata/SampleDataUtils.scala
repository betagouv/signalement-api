package models.report.sampledata

import models.report.Gender.Female
import models.report.Gender.Male
import models.report.Gender
import models.report.ReportCategory
import models.report.ReportTag
import utils.EmailAddress
import utils.URL

import java.util.UUID
import scala.util.Random

object SampleDataUtils {

  private def randomWeirdName() = {
    val syllables = Seq(
      "za",
      "me",
      "mi",
      "zo",
      "ku",
      "zou",
      "zazou",
      "zopi",
      "zamba",
      "zapo",
      "zur",
      "kig",
      "zag",
      "zom"
    )
    Random.shuffle(syllables).take(Random.between(1, 4)).mkString("").capitalize
  }

  def randomConsumerUser(contactAgreement: Boolean = true, phone: Option[String] = None): ConsumerUser = {
    val firstName = randomWeirdName()
    val lastName  = randomWeirdName()
    ConsumerUser(
      firstName = firstName,
      lastName = lastName,
      email = EmailAddress(
        s"dev.signalconso+${firstName.toLowerCase}_${lastName.toLowerCase}${Random.nextInt(100)}@gmail.com"
      ),
      contactAgreement = contactAgreement,
      employeeConsumer = Random.nextDouble() > 0.1,
      gender = if (Random.nextBoolean()) Some(Male) else Some(Female),
      phone = phone
    )
  }

  case class ConsumerUser(
      firstName: String,
      lastName: String,
      email: EmailAddress,
      contactAgreement: Boolean,
      employeeConsumer: Boolean,
      gender: Option[Gender],
      phone: Option[String]
  )

  case class SampleReportBlueprint(
      conso: ConsumerUser,
      category: ReportCategory,
      tags: List[ReportTag],
      details: Seq[(String, String)],
      subcategories: List[String],
      website: Option[URL] = None,
      phone: Option[String] = None,
      barcodeProductId: Option[UUID] = None
  )

}
