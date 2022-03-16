package repositories

import models.report.TableauReport
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import javax.inject.Singleton
import repositories.PostgresProfile.api._

import scala.concurrent.ExecutionContext

/** Temporary to explore some data with external tableau team, will be removed as soon as finished
  */
@Singleton
class TableauReportRepository @Inject() (
    dbConfigProvider: DatabaseConfigProvider
)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._

  def reports() =
    db.run(ReportTables.tables.joinLeft(CompanyTables.tables).on(_.companyId === _.id).result)
      .map(_.map { case (s, e) =>
        TableauReport(
          creationDate = s.creationDate,
          ccrfCode = s.ccrfCode,
          companyName = e.map(_.name),
          companyPostalCode = e.flatMap(_.address.postalCode),
          companySiret = e.map(_.siret),
          details = s.details,
          websiteURL = s.websiteURL.host,
          activityCode = e.flatMap(_.activityCode)
        )
      })

}
