package utils

import java.time.OffsetDateTime
import java.util.UUID

import models._
import org.scalacheck.Arbitrary._
import org.scalacheck._

object Fixtures {
    val genUser = for {
        id <- arbitrary[UUID]
        login <- arbitrary[String]
        password <- arbitrary[String]
        activationKey <- arbitrary[String]
        firstName <- Gen.oneOf("Alice", "Bob", "Charles", "Danièle", "Émilien", "Fanny", "Gérard")
        lastName <- Gen.oneOf("Doe", "Durand", "Dupont")
        userRole <- Gen.oneOf(UserRoles.userRoles)
        randInt <- Gen.choose(0, 1000000)
    } yield User(
        id, login, password, Some(activationKey),
        Some(EmailAddress(s"${firstName}.${lastName}.${randInt}@example.com")),
        Some(firstName), Some(lastName), userRole
    )

    val genAdminUser = genUser.map(_.copy(userRole = UserRoles.Admin))
    val genProUser = genUser.map(_.copy(userRole = UserRoles.Pro))
    val genToActivateUser = genUser.map(_.copy(userRole = UserRoles.ToActivate))
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
        firstName <- Gen.oneOf("Alice", "Bob", "Charles", "Danièle", "Émilien", "Fanny", "Gérard")
        lastName <- Gen.oneOf("Doe", "Durand", "Dupont")
        randInt <- Gen.choose(0, 1000000)
        contactAgreement <- Gen.oneOf(false, true)
        employeeConsumer <- Gen.oneOf(false, true)
    } yield Report(
        Some(id), category, List(subcategory), List(), Some(company.id), company.name, company.address, company.postalCode.map(_.substring(0, 2)), Some(company.siret),
        Some(OffsetDateTime.now()), firstName, lastName, EmailAddress(s"${firstName}.${lastName}.${randInt}@example.com"), contactAgreement, employeeConsumer, List(), None
    )
}
