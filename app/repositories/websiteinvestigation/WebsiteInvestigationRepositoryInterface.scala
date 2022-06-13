package repositories.websiteinvestigation

import models.Company
import models.PaginatedResult
import models.investigation.WebsiteInvestigation
import models.investigation.WebsiteInvestigationId
import models.website.Website
import models.website.WebsiteId
import models.website.WebsiteKind
import repositories.TypedCRUDRepositoryInterface

import scala.concurrent.Future

trait WebsiteInvestigationRepositoryInterface
    extends TypedCRUDRepositoryInterface[WebsiteInvestigation, WebsiteInvestigationId] {

  def listWebsiteInvestigation(
      maybeHost: Option[String],
      kinds: Option[Seq[WebsiteKind]],
      maybeOffset: Option[Long],
      maybeLimit: Option[Int]
  ): Future[PaginatedResult[(((Website, Option[WebsiteInvestigation]), Option[Company]), Int)]]

  def get(websiteId: WebsiteId): Future[Option[WebsiteInvestigation]]

}
