package models.report.sampledata

import models.company.Company
import models.report.Gender.Female
import models.report.Gender.Male
import models.report.DetailInputValue
import models.report.Gender
import models.report.Influencer
import models.report.ReportCategory
import models.report.ReportDraft
import models.report.ReportTag
import utils.EmailAddress
import utils.URL

import java.util.Locale
import java.util.UUID
import scala.util.Random

object SampleDataUtils {

  def randomWeirdName() = {
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
  def buildSampleReport(
      company: Company,
      conso: ConsumerUser,
      category: ReportCategory,
      tags: List[ReportTag],
      details: Seq[(String, String)],
      subcategories: List[String],
      website: Option[URL] = None,
      phone: Option[String] = None,
      influencer: Option[Influencer] = None,
      barcodeProductId: Option[UUID] = None,
      french: Boolean = true
  ): ReportDraft = {
    val c = company
    ReportDraft(
      gender = conso.gender,
      category = category.label,
      subcategories = subcategories,
      details = details.map { case (k, v) => DetailInputValue(k, v) }.toList,
      influencer = influencer,
      companyName = Some(c.name),
      companyCommercialName = c.commercialName,
      companyEstablishmentCommercialName = c.establishmentCommercialName,
      companyBrand = c.brand,
      companyAddress = Some(c.address),
      companySiret = Some(c.siret),
      companyActivityCode = c.activityCode,
      companyIsHeadOffice = Some(c.isHeadOffice),
      companyIsOpen = Some(c.isOpen),
      companyIsPublic = Some(c.isPublic),
      websiteURL = website,
      phone = phone,
      firstName = conso.firstName,
      lastName = conso.lastName,
      email = conso.email,
      contactAgreement = conso.contactAgreement,
      consumerPhone = conso.phone,
      consumerReferenceNumber = None,
      employeeConsumer = conso.employeeConsumer,
      forwardToReponseConso = Some(tags.contains(ReportTag.ReponseConso)),
      fileIds = List.empty,
      vendor = None,
      tags = tags,
      reponseconsoCode = None,
      ccrfCode = None,
      lang = Some {
        if (french) {
          Locale.FRENCH
        } else {
          Locale.ENGLISH
        }
      },
      barcodeProductId = barcodeProductId,
      metadata = None,
      train = None,
      station = None,
      rappelConsoId = None
    )
  }

}
