package orchestrators

import models.PaginatedResult
import models.investigation.WebsiteInvestigationCompanyReportCount
import models.investigation.WebsiteInvestigationCompanyReportCount.toApi
import models.website.WebsiteKind
import play.api.Logger
import repositories.websiteinvestigation.WebsiteInvestigationRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsiteInvestigationOrchestrator(
    val repository: WebsiteInvestigationRepositoryInterface
)(implicit
    ec: ExecutionContext
) {

  val logger: Logger = Logger(this.getClass)

  def list(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[WebsiteInvestigationCompanyReportCount]] =
    for {
      websites <- repository.listWebsiteInvestigation(maybeHost, kinds, maybeOffset, maybeLimit)
      _ = logger.debug("Website investigation fetched")
      websitesWithCount = websites.copy(entities = websites.entities.map(toApi))
    } yield websitesWithCount

}
