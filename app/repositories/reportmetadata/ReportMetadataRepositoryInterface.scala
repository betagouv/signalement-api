package repositories.reportmetadata

import models.report.reportmetadata.ReportMetadata
import repositories.CRUDRepositoryInterface

import java.util.UUID
import scala.concurrent.Future

trait ReportMetadataRepositoryInterface extends CRUDRepositoryInterface[ReportMetadata] {

  def setAssignedUser(reportId: UUID, userId: UUID): Future[Int]

}
