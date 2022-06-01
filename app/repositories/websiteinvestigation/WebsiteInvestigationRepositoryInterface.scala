package repositories.websiteinvestigation

import models.Company
import models.PaginatedResult
import models.investigation.WebsiteInvestigation
import models.website.Website
import models.website.WebsiteKind

import scala.concurrent.Future

trait WebsiteInvestigationRepositoryInterface {

  def listWebsiteInvestigation(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[(((Website, Option[WebsiteInvestigation]), Option[Company]), Int)]]
}
