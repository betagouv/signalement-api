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

  def randomConsumerUser(contactAgreement: Boolean = true, phone: Option[String] = None): ConsumerUser = {
    val gender = if (Random.nextBoolean()) Male else Female

    val number = Random.nextInt(100)
    val firstName = gender match {
      case Male   => "Firmin"
      case Female => "Honorine"
    }

    val lastName = s"Conso${number}"
    ConsumerUser(
      firstName = firstName,
      lastName = lastName,
      email = EmailAddress(
        s"dev.signalconso+${firstName.toLowerCase}_${lastName.toLowerCase}}@gmail.com"
      ),
      contactAgreement = contactAgreement,
      employeeConsumer = Random.nextDouble() > 0.1,
      gender = Some(gender),
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
