package utils

import java.time.OffsetDateTime
import java.util.UUID

import models.Event._
import models._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import utils.Constants.ActionEvent.ActionEventValue
import utils.Constants.EventType.EventTypeValue
import utils.Constants.ReportStatus
import utils.Constants.ReportStatus.ReportStatusValue

import scala.util.Random

object Fixtures {
    // Avoids creating strings with null chars because Postgres text fields don't support it.
    // see http://stackoverflow.com/questions/1347646/postgres-error-on-insert-error-invalid-byte-sequence-for-encoding-utf8-0x0
    implicit val arbString: Arbitrary[String] =
        Arbitrary(Gen.identifier.map(_.replaceAll("\u0000", "")))

    val genUser = for {
        id <- arbitrary[UUID]
        password <- arbString.arbitrary
        firstName <- genFirstName
        lastName <- genLastName
        userRole <- Gen.oneOf(UserRoles.userRoles)
        email <- genEmailAddress(firstName, lastName)
    } yield User(id, password, email, firstName, lastName, userRole, None)

    val genFirstName = Gen.oneOf("Alice", "Bob", "Charles", "Danièle", "Émilien", "Fanny", "Gérard")
    val genLastName = Gen.oneOf("Doe", "Durand", "Dupont")
    def genEmailAddress(firstName: String, lastName: String): Gen[EmailAddress] = EmailAddress(s"${firstName}.${lastName}.${Gen.choose(0, 1000000).sample.get}@example.com")

    val genAdminUser = genUser.map(_.copy(userRole = UserRoles.Admin))
    val genProUser = genUser.map(_.copy(userRole = UserRoles.Pro))
    val genDgccrfUser = genUser.map(_.copy(userRole = UserRoles.DGCCRF))

    val genSiren = for {
        randInt <- Gen.choose(0, 999999999)
    } yield SIREN("" + randInt takeRight 9)

    def genSiret(siren: Option[SIREN] = None) = for {
        randInt <- Gen.choose(0, 99999)
        sirenGen <- genSiren
    } yield SIRET(siren.getOrElse(sirenGen).value + ("" + randInt takeRight 5))

    def genAddress(postalCode: Option[String] = Some("37500")) = for {
        address <- arbString.arbitrary
    } yield Address(
        number = Some(address),
        street = Some(address),
        addressSupplement = Some(address),
        postalCode = postalCode,
        city = Some(address),
    )

    val genCompany = for {
        id <- arbitrary[UUID]
        name <- arbString.arbitrary
        siret <- genSiret()
        address <- genAddress()
    } yield Company(
        siret = siret,
        name = name,
        address = address,
        activityCode = None
    )

    def genCompanyData(company: Option[Company] = None) = for {
        id <- arbitrary[UUID]
        siret <- genSiret()
        denom <- arbString.arbitrary
    } yield CompanyData(
        id, company.map(_.siret).getOrElse(siret), SIREN(company.map(_.siret).getOrElse(siret)), None, None, None, None, None, None, None, None, None, None, None, None, None, None, Some(denom), None, "", None
    )

    val genWebsiteURL = for {
        randInt <- Gen.choose(0, 1000000)
    } yield URL(s"https://www.example${randInt}.com")

    val genReportedPhone = for {
        randInt <- Gen.choose(0, 999999999)
    } yield randInt.toString

    def genDraftReport = for {
        category <- arbString.arbitrary
        subcategory <- arbString.arbitrary
        firstName <- genFirstName
        lastName <- genLastName
        email <- genEmailAddress(firstName, lastName)
        contactAgreement <- arbitrary[Boolean]
        company <- genCompany
        websiteURL <- genWebsiteURL
    } yield DraftReport(
        category = category,
        subcategories = List(subcategory),
        details = List(),
        companyName = Some(company.name),
        companyAddress = Some(company.address),
        companySiret = Some(company.siret),
        companyActivityCode = None,
        websiteURL = Some(websiteURL),
        phone = None,
        firstName = firstName,
        lastName = lastName,
        email = email,
        contactAgreement = contactAgreement,
        employeeConsumer = false,
        fileIds = List.empty
    )

    def genReportForCompany(company: Company) = for {
        id <- arbitrary[UUID]
        category <- arbString.arbitrary
        subcategory <- arbString.arbitrary
        firstName <- genFirstName
        lastName <- genLastName
        email <- genEmailAddress(firstName, lastName)
        contactAgreement <- arbitrary[Boolean]
        status <- Gen.oneOf(ReportStatus.reportStatusList)
    } yield Report(
        id = id,
        category = category,
        subcategories = List(subcategory),
        details = List(),
        companyId = Some(company.id),
        companyName = Some(company.name),
        companyAddress = company.address,
        companySiret = Some(company.siret),
        websiteURL = WebsiteURL(None, None),
        phone = None,
        firstName = firstName,
        lastName = lastName,
        email = email,
        contactAgreement = contactAgreement,
        employeeConsumer = false,
        status = status
    )

    def genReportsForCompanyWithStatus(company: Company, status: ReportStatusValue) =
        Gen.listOfN(Random.nextInt(10), genReportForCompany(company).map(_.copy(status = status)))

    def genReportConsumer = for {
        firstName <- genFirstName
        lastName <- genLastName
        email <- genEmailAddress(firstName, lastName)
        contactAgreement <- arbitrary[Boolean]
    } yield ReportConsumer(firstName, lastName, email, contactAgreement)

    def genReportCompany = for {
        name <- arbString.arbitrary
        address <- genAddress(postalCode = Some(Gen.choose(10000, 99999).toString))
        siret <- genSiret()
    } yield ReportCompany(name, address, siret, None)

    def genReviewOnReportResponse = for {
        positive <- arbitrary[Boolean]
        details <- arbString.arbitrary
    } yield ReviewOnReportResponse(positive, Some(details))

    def genEventForReport(reportId: UUID, eventType: EventTypeValue, actionEvent: ActionEventValue) = for {
        id <- arbitrary[UUID]
        companyId <- arbitrary[UUID]
        details <- arbString.arbitrary
    } yield Event(Some(id), Some(reportId), Some(companyId), None, Some(OffsetDateTime.now()), eventType, actionEvent, stringToDetailsJsValue(details))

    def genEventForCompany(companyId: UUID, eventType: EventTypeValue, actionEvent: ActionEventValue) = for {
        id <- arbitrary[UUID]
        details <- arbString.arbitrary
    } yield Event(Some(id), None, Some(companyId), None, Some(OffsetDateTime.now()), eventType, actionEvent, stringToDetailsJsValue(details))

    def genWebsite() = for {
        id <- arbitrary[UUID]
        companyId <- arbitrary[UUID]
        websiteUrl <- genWebsiteURL
        kind <- Gen.oneOf(WebsiteKind.values)
    } yield Website(id, OffsetDateTime.now(), websiteUrl.getHost.get, companyId, kind)

}
