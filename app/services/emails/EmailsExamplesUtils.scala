package services.emails

import models.Subscription
import models.User
import models.UserRole
import models.auth.AuthToken
import models.company.Address
import models.company.Company
import models.event.Event
import models.report.DetailInputValue.toDetailInputValue
import models.report.ExistingResponseDetails.ME_CONFORMER_A_LA_REGLEMENTATION
import models.report.ReportFileOrigin.Consumer
import models.report._
import models.report.reportfile.ReportFileId
import models.report.review.EngagementReview
import models.report.review.ResponseConsumerReview
import models.report.review.ResponseConsumerReviewId
import models.report.review.ResponseEvaluation.Negative
import models.report.review.ResponseEvaluation.Neutral
import utils.Constants.ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
import utils.Constants.EventType
import utils.EmailAddress
import utils.SIREN
import utils.SIRET

import java.time.OffsetDateTime
import java.time.OffsetDateTime.now
import java.util.Locale
import java.util.UUID
import java.util.UUID.randomUUID

object EmailsExamplesUtils {

  val dummyURL = java.net.URI.create("https://lien-test")

  def genReport = Report(
    id = UUID.fromString("c1cbadb3-04d8-4765-9500-796e7c1f2a6c"),
    gender = Some(Gender.Female),
    category = "Test",
    subcategories = List("test"),
    details = List(toDetailInputValue("test")),
    companyId = Some(UUID.randomUUID()),
    companyName = Some("Dummy Inc."),
    companyBrand = Some("Dummy Inc. Store"),
    companyCommercialName = Some("Dummy Inc. Commercial Name"),
    companyEstablishmentCommercialName = Some("Dummy Inc. Establishment Commercial Name"),
    companyAddress = Address(Some("3 bis"), Some("Rue des exemples"), None, Some("13006"), Some("Douceville")),
    companySiret = Some(SIRET("12345678912345")),
    companyActivityCode = None,
    websiteURL = WebsiteURL(None, None),
    phone = None,
    creationDate = OffsetDateTime.now(),
    firstName = "John",
    lastName = "Doe",
    email = EmailAddress("john.doe@example.com"),
    contactAgreement = true,
    employeeConsumer = false,
    status = ReportStatus.TraitementEnCours,
    expirationDate = OffsetDateTime.now().plusDays(50),
    influencer = None,
    visibleToPro = true,
    lang = Some(Locale.FRENCH),
    barcodeProductId = None,
    train = None,
    station = None,
    rappelConsoId = None
  )

  def genReportFile = ReportFile(
    id = ReportFileId.generateId(),
    reportId = Some(UUID.fromString("c1cbadb3-04d8-4765-9500-796e7c1f2a6c")),
    creationDate = OffsetDateTime.now(),
    filename = s"${UUID.randomUUID.toString}.png",
    storageFilename = "String",
    origin = Consumer,
    avOutput = None
  )

  def genReportResponse = ExistingReportResponse(
    responseType = ReportResponseType.ACCEPTED,
    responseDetails = Some(ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR),
    otherResponseDetails = None,
    consumerDetails = "blablabla",
    dgccrfDetails = Some("ouplalalala"),
    fileIds = Nil
  )

  def genCompany = Company(
    id = UUID.randomUUID,
    siret = SIRET.fromUnsafe("12345678901234"),
    creationDate = OffsetDateTime.now(),
    name = "Test Entreprise",
    address = Address(
      number = Some("3"),
      street = Some("rue des Champs"),
      postalCode = Some("75015"),
      city = Some("Paris"),
      country = None
    ),
    activityCode = None,
    isHeadOffice = true,
    isOpen = true,
    isPublic = true,
    brand = Some("une super enseigne"),
    commercialName = Some("nom commercial"),
    establishmentCommercialName = Some("nom commercial de l'établissement donc pas le même"),
    albertActivityLabel = Some("salle de sport"),
    albertUpdateDate = Some(OffsetDateTime.now().minusDays(10))
  )

  def genCompanyList = List(genCompany, genCompany, genCompany)

  def genSiren = SIREN.fromSIRET(genCompany.siret)

  def genUser = User(
    id = UUID.randomUUID,
    password = "",
    email = EmailAddress("text@example.com"),
    firstName = "Jeanne",
    lastName = "Dupont",
    userRole = UserRole.Admin,
    lastEmailValidation = None,
  )

  def genEvent =
    Event(
      UUID.randomUUID(),
      None,
      None,
      None,
      OffsetDateTime.now(),
      EventType.PRO,
      POST_ACCOUNT_ACTIVATION_DOC
    )

  def genSubscription = Subscription(
    id = UUID.randomUUID,
    userId = None,
    email = None,
    departments = List("75"),
    countries = Nil,
    withTags = Nil,
    withoutTags = Nil,
    categories = Nil,
    sirets = Nil,
    websites = Nil,
    phones = Nil,
    frequency = java.time.Period.ofDays(1)
  )

  def genAuthToken =
    AuthToken(UUID.randomUUID, UUID.randomUUID, OffsetDateTime.now().plusDays(10))

  def genExistingReportResponse = ExistingReportResponse(
    ReportResponseType.ACCEPTED,
    "Un texte écrit par le pro à l'attention du consommateur",
    Some("Un texte écrit par le pro à l'attention de la DGCCRF uniquement"),
    Nil,
    Some(ME_CONFORMER_A_LA_REGLEMENTATION),
    None
  )

  def genResponseConsumerReview = ResponseConsumerReview(
    id = ResponseConsumerReviewId(randomUUID()),
    reportId = randomUUID(),
    evaluation = Negative,
    creationDate = now(),
    details = Some("Texte écrit par le consommateur pour la DGGCRF uniquement")
  )

  def genEngagementReview = EngagementReview(
    id = ResponseConsumerReviewId(randomUUID()),
    reportId = randomUUID(),
    evaluation = Neutral,
    creationDate = now(),
    details = Some("Texte écrit par le consommateur pour la DGGCRF uniquement")
  )
}
