package utils

import java.time.OffsetDateTime
import java.util.UUID

import models._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import utils.Constants.ReportStatus
import utils.Constants.ReportStatus.ReportStatusValue

import scala.util.Random

object Fixtures {
    val genUser = for {
        id <- arbitrary[UUID]
        password <- arbString.arbitrary
        firstName <- genFirstName
        lastName <- genLastName
        userRole <- Gen.oneOf(UserRoles.userRoles)
        email <- genEmailAddress(firstName, lastName)
    } yield User(id, password, email, firstName, lastName, userRole)

    val genFirstName = Gen.oneOf("Alice", "Bob", "Charles", "Danièle", "Émilien", "Fanny", "Gérard")
    val genLastName = Gen.oneOf("Doe", "Durand", "Dupont")
    def genEmailAddress(firstName: String, lastName: String): Gen[EmailAddress] = EmailAddress(s"${firstName}.${lastName}.${Gen.choose(0, 1000000).sample.get}@example.com")

    val genAdminUser = genUser.map(_.copy(userRole = UserRoles.Admin))
    val genProUser = genUser.map(_.copy(userRole = UserRoles.Pro))
    val genDgccrfUser = genUser.map(_.copy(userRole = UserRoles.DGCCRF))

    val genSiret = for {
        randInt <- Gen.choose(0, 1000000)
    } yield "000000000" + randInt takeRight 9

    val genCompany = for {
        id <- arbitrary[UUID]
        name <- arbString.arbitrary
        siret <- genSiret
    } yield Company(
        id, siret, OffsetDateTime.now(), name, "42 rue du Test", Some("37500")
    )

    def genDraftReport = for {
        category <- arbString.arbitrary
        subcategory <- arbString.arbitrary
        firstName <- genFirstName
        lastName <- genLastName
        email <- genEmailAddress(firstName, lastName)
        contactAgreement <- arbitrary[Boolean]
        company <- genCompany
    } yield DraftReport(
        category, List(subcategory), List(), company.name, company.address, company.postalCode.map(_.substring(0, 2)).get, company.siret,
        firstName, lastName, email, contactAgreement, false, List.empty
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
        id, category, List(subcategory), List(), Some(company.id), company.name, company.address, company.postalCode.map(_.substring(0, 2)), Some(company.siret),
        OffsetDateTime.now(), firstName, lastName, email, contactAgreement, false, status
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
        address <- arbString.arbitrary
        siret <- genSiret
        postalCode <- Gen.choose(10000, 99999)
    } yield ReportCompany(name, address, postalCode.toString, siret)
}
