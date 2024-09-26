package models.report.sampledata

import cats.implicits.catsSyntaxOptionId
import models.company.Company
import models.report.DetailInputValue
import models.report.Gender
import models.report.Influencer
import models.report.ReportCategory
import models.report.ReportDraft
import models.report.ReportTag
import models.report.Gender.Female
import models.report.Gender.Male
import models.report.SocialNetworkSlug.TikTok
import utils.EmailAddress
import utils.URL

import java.util.Locale
import scala.util.Random

object ReportGenerator {

  // A list of example subcategories for generating reports
  val subcategories = List(
    "Subcategory A",
    "Subcategory B",
    "Subcategory C"
  )

  // Example detail values to use in reports
  val detailValues = List(
    DetailInputValue("Detail A", "Value A"),
    DetailInputValue("Detail B", "Value B"),
    DetailInputValue("Detail C", "Value C")
  )

  case class ConsumerUser(
      firstName: String,
      lastName: String,
      email: EmailAddress,
      contactAgreement: Boolean,
      employeeConsumer: Boolean,
      gender: Option[Gender]
  )

  // Generate a random report
  def generateReport(
      category: ReportCategory,
      companyOrPostalCode: Either[String, Company],
      website: Option[URL],
      phone: Option[String],
      influencer: Option[Influencer],
      consumerUser: ConsumerUser,
      tags: List[ReportTag],
      expired: Boolean,
      french: Boolean
  ): ReportDraft =
    ReportDraft(
      gender = consumerUser.gender,
      category = category.label,
      subcategories = List(subcategories(Random.nextInt(subcategories.size))),
      details = detailValues,
      influencer = influencer,
      companyName = companyOrPostalCode.map(_.name).toOption,
      companyCommercialName = companyOrPostalCode.map(_.commercialName).toOption.flatten,
      companyEstablishmentCommercialName = companyOrPostalCode.map(_.establishmentCommercialName).toOption.flatten,
      companyBrand = companyOrPostalCode.map(_.brand).toOption.flatten,
      companyAddress = companyOrPostalCode.map(_.address).toOption,
      companySiret = companyOrPostalCode.map(_.siret).toOption,
      companyActivityCode = companyOrPostalCode.map(_.activityCode).toOption.flatten,
      companyIsHeadOffice = companyOrPostalCode.map(_.isHeadOffice).toOption,
      companyIsOpen = companyOrPostalCode.map(_.isOpen).toOption,
      companyIsPublic = companyOrPostalCode.map(_.isPublic).toOption,
      websiteURL = website,
      phone = phone,
      firstName = consumerUser.firstName,
      lastName = consumerUser.lastName,
      email = consumerUser.email,
      contactAgreement = consumerUser.contactAgreement,
      consumerPhone = None,
      consumerReferenceNumber = None,
      employeeConsumer = consumerUser.employeeConsumer,
      forwardToReponseConso = tags.contains(ReportTag.ReponseConso).some,
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
      barcodeProductId = None,
      metadata = None,
      train = None,
      station = None,
      rappelConsoId = None
    )

  private def generateConsumerUser: ConsumerUser = {
    val i = Random.nextInt(100)
    ConsumerUser(
      firstName = s"Prénom$i",
      lastName = s"Nom$i",
      email = EmailAddress(s"dev.signalconso+sample_consumer${i}@gmail.com"),
      contactAgreement = i % 2 == 0,
      employeeConsumer = false,
      gender = if (i % 2 == 0) Some(Male) else Some(Female)
    )
  }

  def visibleReports(company: Company, expired: Boolean) =
    ReportTag.values.collect {
      case ReportTag.LitigeContractuel =>
        generateReport(
          ReportCategory.TelEauGazElec,
          Right(company),
          website = None,
          phone = None,
          influencer = None,
          consumerUser = generateConsumerUser,
          tags = List(ReportTag.LitigeContractuel),
          expired = expired,
          french = true
        )
      case tag @ ReportTag.ProduitDangereux =>
        generateReport(
          ReportCategory.CafeRestaurant,
          Right(company),
          website = None,
          phone = None,
          influencer = None,
          consumerUser = generateConsumerUser,
          tags = List(tag),
          expired = expired,
          french = true
        )
      case tag @ ReportTag.DemarchageTelephonique =>
        generateReport(
          ReportCategory.DemarchageAbusif,
          Right(company),
          website = None,
          phone = Some("0900000000"),
          influencer = None,
          consumerUser = generateConsumerUser,
          tags = List(tag),
          expired = expired,
          french = true
        )
      case tag @ ReportTag.Influenceur =>
        generateReport(
          ReportCategory.AchatMagasin,
          Right(company),
          website = None,
          phone = None,
          influencer = Some(Influencer(Some(TikTok), None, "InfluencerPseudo")),
          consumerUser = generateConsumerUser,
          tags = List(tag),
          expired = expired,
          french = true
        )
      case tag @ ReportTag.ReponseConso =>
        generateReport(
          ReportCategory.AchatMagasin,
          Right(company),
          website = None,
          phone = None,
          influencer = None,
          consumerUser = generateConsumerUser,
          tags = List(tag),
          expired = expired,
          french = true
        )
      case tag @ ReportTag.Internet =>
        generateReport(
          ReportCategory.AchatInternet,
          Right(company),
          website = Some(URL("http://arnaques.com")),
          phone = None,
          influencer = None,
          consumerUser = generateConsumerUser,
          tags = List(tag),
          expired = expired,
          french = true
        )

      case tag @ ReportTag.OpenFoodFacts =>
        generateReport(
          ReportCategory.AchatMagasin,
          Right(company),
          website = None,
          phone = None,
          influencer = None,
          consumerUser = generateConsumerUser,
          tags = List(tag),
          expired = expired,
          french = true
        )
    }.toList

}
