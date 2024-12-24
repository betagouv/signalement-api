package repositories.reportmetadata

import models.report.ConsumerIp
import models.report.reportmetadata.Os
import models.report.reportmetadata.ReportMetadata
import repositories.DatabaseTable
import repositories.PostgresProfile.api._
import repositories.report.ReportTable
import repositories.reportmetadata.ReportMetadataColumnType._
import slick.ast.ColumnOption.PrimaryKey
import slick.collection.heterogeneous.HNil
import slick.collection.heterogeneous.syntax.::
import slick.collection.heterogeneous.syntax._

import java.util.UUID

class ReportMetadataTable(tag: Tag) extends DatabaseTable[ReportMetadata](tag, "reports_metadata") {

  def reportId       = column[UUID]("report_id", PrimaryKey)
  def isMobileApp    = column[Boolean]("is_mobile_app")
  def os             = column[Option[Os]]("os")
  def assignedUserId = column[Option[UUID]]("assigned_user_id")
  def consumerIp     = column[Option[ConsumerIp]]("consumer_ip")

  def report = foreignKey("fk_reports", reportId, ReportTable.table)(
    _.id,
    onUpdate = ForeignKeyAction.Restrict,
    onDelete = ForeignKeyAction.Cascade
  )

  def construct(data: ReportMetadataData): ReportMetadata = data match {
    case reportId ::
        isMobileApp ::
        os ::
        assignedUserId ::
        consumerIp ::
        HNil =>
      ReportMetadata(
        reportId = reportId,
        isMobileApp = isMobileApp,
        os = os,
        assignedUserId = assignedUserId,
        consumerIp = consumerIp
      )
  }

  def extract(rm: ReportMetadata): Option[ReportMetadataData] = Some(
    rm.reportId ::
      rm.isMobileApp ::
      rm.os ::
      rm.assignedUserId ::
      rm.consumerIp ::
      HNil
  )

  type ReportMetadataData =
    UUID ::
      Boolean ::
      Option[Os] ::
      Option[UUID] ::
      Option[ConsumerIp] ::
      HNil

  def * = (
    reportId ::
      isMobileApp ::
      os ::
      assignedUserId ::
      consumerIp ::
      HNil
  ) <> (construct, extract)
}

object ReportMetadataTable {

  val table = TableQuery[ReportMetadataTable]

}
