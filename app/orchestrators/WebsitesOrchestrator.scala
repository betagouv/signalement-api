package orchestrators

import models.PaginatedResult
import models.website.WebsiteCompanyReportCount
import models.website.WebsiteCompanyReportCount.toDomain
import play.api.Logger
import repositories.WebsiteRepository

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsitesOrchestrator @Inject() (val repository: WebsiteRepository)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  def getWebsiteCompanyCount(
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[WebsiteCompanyReportCount]] =
    for {
      websites <- repository.listWebsitesCompaniesByReportCount(maybeOffset, maybeLimit)
      _ = logger.debug("Website company report fetched")
      websitesWithCount = websites.copy(entities = websites.entities.map(toDomain))
    } yield websitesWithCount

}
