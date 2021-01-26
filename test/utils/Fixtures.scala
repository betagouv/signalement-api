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

    val genSiret = for {
        randInt <- Gen.choose(0, 1000000)
    } yield SIRET("000000000" + randInt takeRight 14)

    val genAddress = for {
        address <- arbString.arbitrary
    } yield Address(address)

    val genCompany = for {
        id <- arbitrary[UUID]
        name <- arbString.arbitrary
        siret <- genSiret
        address <- genAddress
    } yield Company(
        id, siret, OffsetDateTime.now(), name, address, Some("37500"), None
    )

    val genWebsiteURL = for {
        randInt <- Gen.choose(0, 1000000)
    } yield URL(s"https://www.example${randInt}.com")

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
        category, List(subcategory), List(), Some(company.name), Some(company.address), company.postalCode.map(_.substring(0, 2)), None, Some(company.siret),
        None, Some(websiteURL), firstName, lastName, email, contactAgreement, false, List.empty
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
        id, category, List(subcategory), List(), Some(company.id), Some(company.name), Some(company.address), company.postalCode.map(_.substring(0, 2)), None, Some(company.siret),
        None, OffsetDateTime.now(), firstName, lastName, email, contactAgreement, false, status
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
        address <- genAddress
        siret <- genSiret
        postalCode <- Gen.choose(10000, 99999)
    } yield ReportCompany(name, address, postalCode.toString, siret, None)

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
