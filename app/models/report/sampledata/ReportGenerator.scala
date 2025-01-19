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
import models.report.ReportCategory.AchatMagasin
import models.report.ReportCategory.CafeRestaurant
import models.report.ReportCategory.DemarchageAbusif
import models.report.ReportCategory.TelEauGazElec
import models.report.ReportTag.DemarchageTelephonique
import models.report.ReportTag.Influenceur
import models.report.ReportTag.Internet
import models.report.ReportTag.LitigeContractuel
import models.report.ReportTag.OpenFoodFacts
import models.report.ReportTag.ProduitDangereux
import models.report.ReportTag.ReponseConso
import models.report.SocialNetworkSlug.TikTok
import models.report.sampledata.SampleDescriptions.sampleDescriptions
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
  private def generateDetailValues() = List(
    ("Detail A :", "Value A"),
    ("Detail B :", "Value B"),
    ("Description :", Random.shuffle(sampleDescriptions).head)
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
  private def generateReport(
      conso: ConsumerUser,
      company: Company,
      category: ReportCategory,
      tags: List[ReportTag],
      details: Seq[(String, String)] = generateDetailValues(),
      website: Option[URL] = None,
      phone: Option[String] = None,
      influencer: Option[Influencer] = None,
      french: Boolean = true
  ): ReportDraft = {
    val c = company
    ReportDraft(
      gender = conso.gender,
      category = category.label,
      subcategories = List(subcategories(Random.nextInt(subcategories.size))),
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
      consumerPhone = None,
      consumerReferenceNumber = None,
      employeeConsumer = conso.employeeConsumer,
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
  }

  private def generateConsumerUser(): ConsumerUser = {
    val firstName = weirdRandomName()
    val lastName  = weirdRandomName()
    ConsumerUser(
      firstName = firstName,
      lastName = lastName,
      email = EmailAddress(
        s"dev.signalconso+${firstName.toLowerCase}_${lastName.toLowerCase}${Random.nextInt(100)}@gmail.com"
      ),
      contactAgreement = Random.nextDouble() > 0.3,
      employeeConsumer = Random.nextDouble() > 0.1,
      gender = if (Random.nextBoolean()) Some(Male) else Some(Female)
    )
  }

  def generateRandomNumberOfReports(company: Company, reportsAmountFactor: Double = 1): List[ReportDraft] = {
    val n = Math.max(1, Math.round(Random.between(1, 4) * reportsAmountFactor).toInt)
    (1 to n).map(_ => generateRandomReport(company)).toList
  }

  private def generateRandomReport(company: Company) = {
    val randomReportsList = List(
      generateReport(
        conso = generateConsumerUser(),
        company,
        TelEauGazElec,
        tags = List(LitigeContractuel)
      ),
      generateReport(
        conso = generateConsumerUser(),
        company,
        CafeRestaurant,
        tags = List(ProduitDangereux)
      ),
      generateReport(
        conso = generateConsumerUser(),
        company,
        DemarchageAbusif,
        phone = Some("0900000000"),
        tags = List(DemarchageTelephonique)
      ),
      generateReport(
        conso = generateConsumerUser(),
        company,
        AchatMagasin,
        tags = List(Influenceur),
        influencer = Some(Influencer(Some(TikTok), None, "InfluencerPseudo"))
      ),
      generateReport(
        conso = generateConsumerUser(),
        company,
        AchatMagasin,
        tags = List(ReponseConso)
      ),
      generateReport(
        conso = generateConsumerUser(),
        company,
        ReportCategory.AchatInternet,
        tags = List(Internet)
        // doesn't work anymore, it seems to break some db constraint ?
//        website = Some(URL("http://arnaques.com"))
      ),
      generateReport(
        conso = generateConsumerUser(),
        company,
        AchatMagasin,
        tags = List(OpenFoodFacts)
      )
    )
    Random.shuffle(randomReportsList).head
  }

  private def weirdRandomName() = {
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
}
