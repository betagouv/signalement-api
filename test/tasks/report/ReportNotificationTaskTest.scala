package tasks.report

import models.Subscription
import models.UserRole
import models.company.Address
import models.report.ReportCategory
import models.report.ReportFile
import models.report.ReportTag
import org.specs2.mutable.Specification
import repositories.report.ReportRepository.ReportOrdering
import utils.Country.Bangladesh
import utils.Fixtures

import java.util.UUID
import scala.collection.immutable.SortedMap

class ReportNotificationTaskTest extends Specification {

  "ReportNotificationTaskTest" should {

    "filter subscription report on postal code only when postal code is not in a foreign country" in {

      val department     = "75"
      val company        = Fixtures.genCompany.sample.get
      val frenchAddress  = Address(country = None, postalCode = Some(department))
      val foreignAddress = Address(country = Some(Bangladesh), postalCode = Some(department))
      val baseReport     = Fixtures.genReportForCompany(company).sample.get

      val frenchReport  = baseReport.copy(id = UUID.randomUUID(), companyAddress = frenchAddress)
      val foreignReport = baseReport.copy(id = UUID.randomUUID(), companyAddress = foreignAddress)

      val reports = List((frenchReport, List.empty[ReportFile]), (foreignReport, List.empty[ReportFile]))

      val reportsMap = SortedMap.from(reports)(ReportOrdering)

      val subscription =
        new Subscription(
          userId = None,
          email = None,
          departments = List(department),
          frequency = java.time.Period.ofDays(1),
          userRole = Some(UserRole.Admin)
        )

      val res = ReportNotificationTask.refineReportBasedOnSubscriptionFilters(reportsMap, subscription)

      res.keys.toList shouldEqual List(frenchReport)

    }

    "filter reports when user is DGAL" in {
      val company    = Fixtures.genCompany.sample.get
      val baseReport = Fixtures.genReportForCompany(company).sample.get

      val accessibleReport1 =
        baseReport.copy(id = UUID.randomUUID(), category = ReportCategory.IntoxicationAlimentaire.entryName)
      val accessibleReport2 = baseReport.copy(id = UUID.randomUUID(), tags = List(ReportTag.Hygiene))
      val accessibleReport3 = baseReport.copy(id = UUID.randomUUID(), tags = List(ReportTag.ProduitAlimentaire))
      val inaccessibleReport = baseReport.copy(
        id = UUID.randomUUID(),
        tags = List(ReportTag.ProduitDangereux),
        category = ReportCategory.AchatMagasin.entryName
      )

      val reports = List(
        (accessibleReport1, List.empty[ReportFile]),
        (accessibleReport2, List.empty[ReportFile]),
        (accessibleReport3, List.empty[ReportFile]),
        (inaccessibleReport, List.empty[ReportFile])
      )

      val reportsMap = SortedMap.from(reports)(ReportOrdering)

      val subscription =
        new Subscription(
          userId = None,
          email = None,
          departments = List.empty,
          frequency = java.time.Period.ofDays(1),
          userRole = Some(UserRole.DGAL)
        )

      val res = ReportNotificationTask.refineReportBasedOnSubscriptionFilters(reportsMap, subscription)

      res.keys.toList should contain(accessibleReport1, accessibleReport2, accessibleReport3)
    }
  }

}
