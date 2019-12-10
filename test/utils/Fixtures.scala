package utils

import java.time.OffsetDateTime
import java.util.UUID

import models._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import utils.Constants.ReportStatus.ReportStatusValue

import scala.util.Random

object Fixtures {
    val genUser = for {
        id <- arbitrary[UUID]
        password <- arbitrary[String]
        firstName <- genFirstName
        lastName <- genLastName
        userRole <- Gen.oneOf(UserRoles.userRoles)
        email <- genEmailAddress(firstName, lastName)
    } yield User(
        id, password, Some(email),
        Some(firstName), Some(lastName), userRole
    )

    val genFirstName = Gen.oneOf("Alice", "Bob", "Charles", "Danièle", "Émilien", "Fanny", "Gérard")
    val genLastName = Gen.oneOf("Doe", "Durand", "Dupont")
    def genEmailAddress(firstName: String, lastName: String): Gen[EmailAddress] = EmailAddress(s"${firstName}.${lastName}.${Gen.choose(0, 1000000)}@example.com")

    val genAdminUser = genUser.map(_.copy(userRole = UserRoles.Admin))
    val genProUser = genUser.map(_.copy(userRole = UserRoles.Pro))
    val genDgccrfUser = genUser.map(_.copy(userRole = UserRoles.DGCCRF))

    val genCompany = for {
        id <- arbitrary[UUID]
        name <- arbitrary[String]
        randInt <- Gen.choose(0, 1000000)
    } yield Company(
        id, "000000000" + randInt takeRight 9, OffsetDateTime.now(),
        name, "42 rue du Test", Some("37500")
    )

    def genReportForCompany(company: Company) = for {
        id <- arbitrary[UUID]
        category <- arbitrary[String]
        subcategory <- arbitrary[String]
        firstName <- genFirstName
        lastName <- genLastName
        email <- genEmailAddress(firstName, lastName)
        contactAgreement <- arbitrary[Boolean]
        employeeConsumer <- arbitrary[Boolean]
    } yield Report(
        Some(id), category, List(subcategory), List(), Some(company.id), company.name, company.address, company.postalCode.map(_.substring(0, 2)), Some(company.siret),
        Some(OffsetDateTime.now()), firstName, lastName, email, contactAgreement, employeeConsumer, List(), None
    )

    def genReportsForCompanyWithStatus(company: Company, status: Option[ReportStatusValue]) =
        Gen.listOfN(Random.nextInt(10), genReportForCompany(company).map(_.copy(status = status)))
}
