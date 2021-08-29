package orchestrators

import models.PaginatedResult
import models.WebsiteKind
import models.website.WebsiteCompanyReportCount
import models.website.WebsiteCompanyReportCount.toApi
import play.api.Logger
import repositories.WebsiteRepository
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsitesOrchestrator @Inject() (val repository: WebsiteRepository)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  def getWebsiteCompanyCount(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[WebsiteCompanyReportCount]] =
    for {
      websites <- repository.listWebsitesCompaniesByReportCount(maybeHost, kinds, maybeOffset, maybeLimit)
      _ = logger.debug("Website company report fetched")
      websitesWithCount = websites.copy(entities = websites.entities.map(toApi))
    } yield websitesWithCount

}
