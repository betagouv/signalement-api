package repositories.reportfile

import com.google.inject.ImplementedBy
import models.report.ReportFile
import repositories.CRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

@ImplementedBy(classOf[ReportFileRepository])
trait ReportFileRepositoryInterface extends CRUDRepositoryInterface[ReportFile] {

  def attachFilesToReport(fileIds: List[UUID], reportId: UUID): Future[Int]

  def retrieveReportFiles(reportId: UUID): Future[List[ReportFile]]

  def prefetchReportsFiles(reportsIds: List[UUID]): Future[Map[UUID, List[ReportFile]]]

  def setAvOutput(fileId: UUID, output: String): Future[Int]

  def removeStorageFileName(fileId: UUID): Future[Int]
}
