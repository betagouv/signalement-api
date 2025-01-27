package models.report.sampledata

import models.report.Gender
import models.report.Gender.Female
import models.report.Gender.Male
import utils.EmailAddress

import scala.util.Random

object ConsoUserGenerator {
  case class ConsumerUser(
      firstName: String,
      lastName: String,
      email: EmailAddress,
      contactAgreement: Boolean,
      gender: Option[Gender],
      phone: Option[String]
  )
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
      gender = Some(gender),
      phone = phone
    )
  }
}
