package orchestrators

import models.CountByDate
import models.report.ArborescenceNode
import models.report.CategoryInfo
import models.report.NodeInfo
import models.report.ReportNode
import orchestrators.StatsOrchestrator.formatStatData
import orchestrators.StatsOrchestrator.buildReportNodes
import org.specs2.mutable.Specification

import java.sql.Timestamp
import java.time.LocalDate
import java.util.Locale

class StatsOrchestratorTest extends Specification {

  "Create stats tree" should {
    "correctly convert data" in {
      val arborescence = List(
        ArborescenceNode(
          None,
          Vector(
            CategoryInfo("cat1", "Cat 1")         -> NodeInfo("1", List("tag1")),
            CategoryInfo("subcat11", "Subcat 11") -> NodeInfo("1.1", List.empty)
          )
        ),
        ArborescenceNode(
          None,
          Vector(
            CategoryInfo("cat2", "Cat 2")         -> NodeInfo("2", List.empty),
            CategoryInfo("subcat21", "Subcat 21") -> NodeInfo("2.1", List("tag2"))
          )
        ),
        ArborescenceNode(
          None,
          Vector(
            CategoryInfo("cat2", "Cat 2")         -> NodeInfo("2", List.empty),
            CategoryInfo("subcat22", "Subcat 22") -> NodeInfo("2.2", List.empty)
          )
        ),
        ArborescenceNode(
          None,
          Vector(CategoryInfo("cat3", "Cat 3") -> NodeInfo("3", List.empty))
        )
      )

      val expected =
        List(
          ReportNode("cat4", "cat4", 10, 5, List.empty, List.empty, None),
          ReportNode("cat3", "Cat 3", 0, 0, List.empty, List.empty, Some("3")),
          ReportNode(
            "cat2",
            "Cat 2",
            3,
            2,
            List(
              ReportNode("subcat22", "Subcat 22", 2, 1, List.empty, List.empty, Some("2.2")),
              ReportNode("subcat21", "Subcat 21", 1, 1, List.empty, List("tag2"), Some("2.1"))
            ),
            List.empty,
            Some("2")
          ),
          ReportNode(
            "cat1",
            "Cat 1",
            1,
            0,
            List(ReportNode("subcat11", "Subcat 11", 1, 0, List.empty, List.empty, Some("1.1"))),
            List("tag1"),
            Some("1")
          )
        )
      val inputs = Seq(
        ("cat4", List.empty, 10, 5),
        ("cat2", List("subcat22"), 2, 1),
        ("cat2", List("subcat21"), 1, 1),
        ("cat1", List("subcat11"), 1, 0)
      )
      val res = buildReportNodes(List.empty, Locale.FRENCH, arborescence, inputs)

      res shouldEqual expected
    }
  }

  "StatsOrchestratorTest" should {

    "handle missing data correctly when data are missing on both boundaries" in {

      val now = LocalDate.now().withDayOfMonth(1).atStartOfDay()

      val data = Vector(
        (Timestamp.valueOf(now.minusMonths(3L)), 1234),
        (Timestamp.valueOf(now.minusMonths(2L)), 1234)
      )

      val tick = 5
      val expected = Seq(
        CountByDate(0, now.minusMonths(4L).toLocalDate),
        CountByDate(1234, now.minusMonths(3L).toLocalDate),
        CountByDate(1234, now.minusMonths(2L).toLocalDate),
        CountByDate(0, now.minusMonths(1L).toLocalDate),
        CountByDate(0, now.minusMonths(0L).toLocalDate)
      )

      formatStatData(data, tick) shouldEqual expected

    }

    "handle missing data correctly when data are missing on lower boundary" in {

      val now = LocalDate.now().withDayOfMonth(1).atStartOfDay()

      val data = Vector(
        (Timestamp.valueOf(now.minusMonths(3L)), 1234),
        (Timestamp.valueOf(now.minusMonths(2L)), 1234)
      )

      val tick = 4
      val expected = Seq(
        CountByDate(1234, now.minusMonths(3L).toLocalDate),
        CountByDate(1234, now.minusMonths(2L).toLocalDate),
        CountByDate(0, now.minusMonths(1L).toLocalDate),
        CountByDate(0, now.minusMonths(0L).toLocalDate)
      )

      formatStatData(data, tick) shouldEqual expected

    }

    "handle missing data correctly when data are missing on upper boundary" in {

      val now = LocalDate.now().withDayOfMonth(1).atStartOfDay()

      val data = Vector(
        (Timestamp.valueOf(now.minusMonths(1L)), 1234),
        (Timestamp.valueOf(now.minusMonths(0L)), 1234)
      )

      val tick = 3
      val expected = Seq(
        CountByDate(0, now.minusMonths(2L).toLocalDate),
        CountByDate(1234, now.minusMonths(1L).toLocalDate),
        CountByDate(1234, now.minusMonths(0L).toLocalDate)
      )

      formatStatData(data, tick) shouldEqual expected

    }

    "handle missing data correctly when data are available for all ticks" in {

      val now = LocalDate.now().withDayOfMonth(1).atStartOfDay()

      val data = Vector(
        (Timestamp.valueOf(now.minusMonths(1L)), 1234),
        (Timestamp.valueOf(now.minusMonths(0L)), 1234)
      )

      val tick = 2
      val expected = Seq(
        CountByDate(1234, now.minusMonths(1L).toLocalDate),
        CountByDate(1234, now.minusMonths(0L).toLocalDate)
      )

      formatStatData(data, tick) shouldEqual expected

    }

    "handle missing data correctly when no data are returned" in {

      val now = LocalDate.now().withDayOfMonth(1).atStartOfDay()

      val data = Vector(
      )

      val tick = 3
      val expected = Seq(
        CountByDate(0, now.minusMonths(2L).toLocalDate),
        CountByDate(0, now.minusMonths(1L).toLocalDate),
        CountByDate(0, now.minusMonths(0L).toLocalDate)
      )

      formatStatData(data, tick) shouldEqual expected

    }

  }

}
