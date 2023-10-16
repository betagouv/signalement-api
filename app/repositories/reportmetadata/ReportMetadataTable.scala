package repositories.reportmetadata

import models.report.reportmetadata.Os
import models.report.reportmetadata.ReportMetadata
import repositories.DatabaseTable
import repositories.reportmetadata.ReportMetadataColumnType._
import repositories.PostgresProfile.api._
import repositories.report.ReportTable
import slick.collection.heterogeneous.HNil
import slick.collection.heterogeneous.syntax._

import java.util.UUID

class ReportMetadataTable(tag: Tag) extends DatabaseTable[ReportMetadata](tag, "reports_metadata") {

  def reportId    = column[UUID]("report_id")
  def isMobileApp = column[Boolean]("is_mobile_app")
  def os          = column[Option[Os]]("os")

  def report = foreignKey("fk_reports", reportId, ReportTable.table)(
    _.id,
    onUpdate = ForeignKeyAction.Restrict,
    onDelete = ForeignKeyAction.Cascade
  )

  def construct(data: ReportMetadataData): ReportMetadata = data match {
    case reportId ::
        isMobileApp ::
        os ::
        HNil =>
      ReportMetadata(
        reportId = reportId,
        isMobileApp = isMobileApp,
        os = os
      )
  }

  def extract(rm: ReportMetadata): Option[ReportMetadataData] = Some(
    rm.reportId ::
      rm.isMobileApp ::
      rm.os ::
      HNil
  )

  type ReportMetadataData =
    UUID ::
      Boolean ::
      Option[Os] ::
      HNil

  def * = (
    reportId ::
      isMobileApp ::
      os ::
      HNil
  ) <> (construct, extract)
}

object ReportMetadataTable {

  val table = TableQuery[ReportMetadataTable]

}
