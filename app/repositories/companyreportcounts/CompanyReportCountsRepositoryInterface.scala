package repositories.companyreportcounts

import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait CompanyReportCountsRepositoryInterface extends CRUDRepositoryInterface[CompanyReportCounts] {

  def refreshMaterializeView(): Future[Unit]

}
