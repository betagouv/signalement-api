package utils

import models._
import models.barcode.BarcodeProduct
import models.company.Address
import models.company.Company
import models.event.Event
import models.event.Event._
import models.report._
import models.website.IdentificationStatus
import models.website.Website
import models.website.WebsiteId
import org.scalacheck.Arbitrary._
import org.scalacheck._
import play.api.libs.json.Json
import tasks.company.CompanySearchResult
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType.EventTypeValue

import java.time.OffsetDateTime
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.UUID
import scala.util.Random
object Fixtures {
  // Avoids creating strings with null chars because Postgres text fields don't support it.
  // see http://stackoverflow.com/questions/1347646/postgres-error-on-insert-error-invalid-byte-sequence-for-encoding-utf8-0x0
  implicit val arbString: Arbitrary[String] =
    Arbitrary(Gen.identifier.map(_.replaceAll("\u0000", "")))

  val genGender = Gen.oneOf(Some(Gender.Female), Some(Gender.Male), None)

  val genConsumer = for {
    id     <- arbitrary[UUID]
    name   <- arbString.arbitrary
    apiKey <- arbString.arbitrary
  } yield Consumer(
    id = id,
    name = name,
    apiKey = apiKey
  )

  val genUser = for {
    id        <- arbitrary[UUID]
    password  <- arbString.arbitrary
    firstName <- genFirstName
    lastName  <- genLastName
    userRole  <- Gen.oneOf(UserRole.values)
    email     <- genEmailAddress(firstName, lastName)
  } yield User(
    id = id,
    password = password + "testtesttestA1!",
    email = email,
    firstName = firstName,
    lastName = lastName,
    userRole = userRole,
    lastEmailValidation = None
  )

  val genFirstName = Gen.oneOf("Alice", "Bob", "Charles", "Danièle", "Émilien", "Fanny", "Gérard")
  val genLastName  = Gen.oneOf("Doe", "Durand", "Dupont")
  def genEmailAddress(firstName: String, lastName: String): Gen[EmailAddress] = EmailAddress(
    s"${firstName}.${lastName}.${Gen.choose(0, 1000000).sample.get}@example.com"
  )

  def genEmailAddress: Gen[EmailAddress] = EmailAddress(
    s"${genFirstName.sample.get}.${genLastName.sample.get}.${Gen.choose(0, 1000000).sample.get}@example.com"
  )

  val genAdminUser  = genUser.map(_.copy(userRole = UserRole.Admin))
  val genProUser    = genUser.map(_.copy(userRole = UserRole.Professionnel))
  val genDgccrfUser = genUser.map(_.copy(userRole = UserRole.DGCCRF))

  val genSiren = for {
    randInt <- Gen.choose(0, 999999999)
  } yield SIREN.fromUnsafe("" + randInt takeRight 9)

  def genSiret(siren: Option[SIREN] = None) = for {
    randInt  <- Gen.choose(0, 99999)
    sirenGen <- genSiren
  } yield SIRET.fromUnsafe(siren.getOrElse(sirenGen).value + ("" + randInt takeRight 5))

  def genAddress(postalCode: Option[String] = Some("37500")) = for {
    number            <- arbString.arbitrary
    street            <- arbString.arbitrary
    addressSupplement <- arbString.arbitrary
    city              <- arbString.arbitrary
  } yield Address(
    number = Some("number_" + number),
    street = Some("street_" + street),
    addressSupplement = Some("addressSupplement_" + addressSupplement),
    postalCode = postalCode,
    city = Some("city_" + city)
  )

  val genCompany = for {
    _                           <- arbitrary[UUID]
    name                        <- arbString.arbitrary
    brand                       <- Gen.option(arbString.arbitrary)
    commercialName              <- Gen.option(arbString.arbitrary)
    establishmentCommercialName <- Gen.option(arbString.arbitrary)
    siret                       <- genSiret()
    address                     <- genAddress()
  } yield Company(
    siret = siret,
    name = name,
    address = address,
    creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    activityCode = None,
    isOpen = true,
    isHeadOffice = false,
    isPublic = true,
    brand = brand,
    commercialName = commercialName,
    establishmentCommercialName = establishmentCommercialName
  )

  def genCompanySearchResult(siren: Option[SIREN]) = for {
    siret <- siren
      .map(s => Gen.choose(0, 99999).map(randInt => SIRET.fromUnsafe(s.value + ("" + randInt takeRight 5))))
      .getOrElse(genSiret())
    name                        <- arbString.arbitrary
    brand                       <- Gen.option(arbString.arbitrary)
    commercialName              <- Gen.option(arbString.arbitrary)
    establishmentCommercialName <- Gen.option(arbString.arbitrary)
    address                     <- genAddress()
  } yield CompanySearchResult(
    siret = siret,
    name = Some(name),
    brand = brand,
    commercialName = commercialName,
    establishmentCommercialName = establishmentCommercialName,
    isHeadOffice = false,
    address = address,
    activityCode = None,
    activityLabel = None,
    isOpen = true,
    isPublic = true
  )

  val genInfluencer = for {
    socialNetwork <- Gen.oneOf(SocialNetworkSlug.values)
    name          <- arbString.arbitrary
  } yield Influencer(Some(socialNetwork), None, name)

  val genWebsiteURL = for {
    randInt <- Gen.choose(0, 1000000)
  } yield URL(s"https://${randInt}.com")

  val genReportedPhone = for {
    randInt <- Gen.choose(0, 999999999)
  } yield randInt.toString

  def genDraftReport = for {
    gender           <- genGender
    category         <- arbString.arbitrary
    subcategory      <- arbString.arbitrary
    firstName        <- genFirstName
    lastName         <- genLastName
    email            <- genEmailAddress(firstName, lastName)
    contactAgreement <- arbitrary[Boolean]
    company          <- genCompany
    websiteURL       <- genWebsiteURL
  } yield ReportDraft(
    gender = gender,
    category = category,
    subcategories = List(subcategory),
    details = List(),
    influencer = None,
    companyName = Some(company.name),
    companyBrand = company.brand,
    companyCommercialName = company.commercialName,
    companyEstablishmentCommercialName = company.establishmentCommercialName,
    companyAddress = Some(company.address),
    companySiret = Some(company.siret),
    companyActivityCode = None,
    companyIsHeadOffice = Some(company.isHeadOffice),
    companyIsOpen = Some(company.isOpen),
    companyIsPublic = Some(company.isPublic),
    websiteURL = Some(websiteURL),
    phone = None,
    firstName = firstName,
    lastName = lastName,
    email = email,
    consumerPhone = None,
    consumerReferenceNumber = None,
    contactAgreement = contactAgreement,
    employeeConsumer = false,
    fileIds = List.empty
  )

  def genReportFromDraft(
      reportDraft: ReportDraft,
      maybeCompanyId: Option[UUID] = None,
      maybeCompany: Option[Company] = None
  ): Report = {
    val now   = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    val later = now.plusDays(50)
    val company = for {
      name <- reportDraft.companyName
      brand                       = reportDraft.companyBrand
      commercialName              = reportDraft.companyCommercialName
      establishmentCommercialName = reportDraft.companyEstablishmentCommercialName
      address <- reportDraft.companyAddress
      siret   <- reportDraft.companySiret
      activityCode = reportDraft.companyActivityCode
    } yield Company(
      name = name,
      brand = brand,
      commercialName = commercialName,
      establishmentCommercialName = establishmentCommercialName,
      address = address,
      siret = siret,
      activityCode = activityCode,
      isHeadOffice = false,
      isOpen = false,
      isPublic = false
    )
    reportDraft.generateReport(maybeCompanyId, maybeCompany.orElse(company), creationDate = now, expirationDate = later)
  }

  def genReportCategory: Gen[String] =
    Gen.oneOf(Gen.oneOf(ReportCategory.values.map(_.entryName)), arbString.arbitrary)

  def genReportForCompany(company: Company): Gen[Report] = for {
    id               <- arbitrary[UUID]
    gender           <- genGender
    category         <- genReportCategory
    subcategory      <- arbString.arbitrary
    firstName        <- genFirstName
    lastName         <- genLastName
    email            <- genEmailAddress(firstName, lastName)
    contactAgreement <- arbitrary[Boolean]
    status           <- Gen.oneOf(ReportStatus.values)
  } yield Report(
    id = id,
    gender = gender,
    category = category,
    subcategories = List(subcategory),
    details = List(),
    influencer = None,
    companyId = Some(company.id),
    creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    companyName = Some(company.name),
    companyBrand = company.brand,
    companyCommercialName = company.commercialName,
    companyEstablishmentCommercialName = company.establishmentCommercialName,
    companyAddress = company.address,
    companySiret = Some(company.siret),
    companyActivityCode = company.activityCode,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    firstName = firstName,
    lastName = lastName,
    email = email,
    consumerPhone = None,
    consumerReferenceNumber = None,
    contactAgreement = contactAgreement,
    employeeConsumer = false,
    status = status,
    expirationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    visibleToPro = true,
    lang = None,
    barcodeProductId = None,
    train = None,
    station = None,
    rappelConsoId = None
  )

  def genReportsForCompanyWithStatus(company: Company, status: ReportStatus) =
    Gen.listOfN(Random.nextInt(10), genReportForCompany(company).map(_.copy(status = status)))

  def genReportForCompanyWithStatus(company: Company, status: ReportStatus) =
    genReportForCompany(company).map(_.copy(status = status))

  def genReportConsumerUpdate = for {
    firstName               <- genFirstName
    lastName                <- genLastName
    email                   <- genEmailAddress(firstName, lastName)
    consumerReferenceNumber <- arbString.arbitrary
  } yield ReportConsumerUpdate(firstName, lastName, email, Some(consumerReferenceNumber))

  def genReportCompany = for {
    name                        <- arbString.arbitrary
    brand                       <- Gen.option(arbString.arbitrary)
    commercialName              <- Gen.option(arbString.arbitrary)
    establishmentCommercialName <- Gen.option(arbString.arbitrary)
    address                     <- genAddress(postalCode = Some(Gen.choose(10000, 99999).toString))
    siret                       <- genSiret()
  } yield ReportCompany(
    name,
    brand,
    commercialName,
    establishmentCommercialName,
    address,
    siret,
    None,
    isHeadOffice = true,
    isOpen = true,
    isPublic = true
  )

  def genEventForReport(reportId: UUID, eventType: EventTypeValue, actionEvent: ActionEventValue) = for {
    id        <- arbitrary[UUID]
    companyId <- arbitrary[UUID]
    details   <- arbString.arbitrary
  } yield Event(
    id = id,
    reportId = Some(reportId),
    companyId = Some(companyId),
    userId = None,
    creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    eventType = eventType,
    action = actionEvent,
    details = stringToDetailsJsValue(details)
  )

  def genEventForCompany(companyId: UUID, eventType: EventTypeValue, actionEvent: ActionEventValue) = for {
    id      <- arbitrary[UUID]
    details <- arbString.arbitrary
  } yield Event(
    id = id,
    reportId = None,
    companyId = Some(companyId),
    userId = None,
    creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    eventType = eventType,
    action = actionEvent,
    details = stringToDetailsJsValue(details)
  )

  def genWebsite() = for {
    companyId  <- arbitrary[UUID]
    websiteUrl <- genWebsiteURL
    kind       <- Gen.oneOf(IdentificationStatus.values)
  } yield Website(
    id = WebsiteId.generateId(),
    creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    host = websiteUrl.getHost.get,
    companyCountry = None,
    companyId = Some(companyId),
    lastUpdated = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
    identificationStatus = kind
  )

  def genCountry(): Gen[Country] = Gen.oneOf(Country.countries)

  val genBarcodeProduct = for {
    gtin <- arbString.arbitrary
  } yield BarcodeProduct(
    gtin = gtin,
    gs1Product = Json.obj(),
    openFoodFactsProduct = None,
    openBeautyFactsProduct = None
  )

  def genSubscriptionFor(userId: Option[UUID]) = for {
    email <- Gen.option(genEmailAddress)
    days  <- Gen.choose(0, 31)
  } yield Subscription(userId = userId, email = email, frequency = Period.of(0, 0, days))
}
