package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.WebsiteNotFound
import models.PaginatedResult
import models.website.Website
import models.website.WebsiteCompany
import models.website.WebsiteCompanyReportCount
import models.website.WebsiteKind
import models.website.WebsiteUpdate
import models.website.WebsiteCompanyReportCount.toApi
import play.api.Logger
import repositories.CompanyRepository
import repositories.WebsiteRepository

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class WebsitesOrchestrator @Inject() (
    val repository: WebsiteRepository,
    val companyRepository: CompanyRepository
)(implicit
    ec: ExecutionContext
) {

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

  def update(websiteId: UUID, websiteUpdate: WebsiteUpdate): Future[WebsiteCompany] = for {
    maybeWebsite <- repository.find(websiteId)
    website <- maybeWebsite.liftTo[Future](WebsiteNotFound(websiteId))
    _ <-
      if (websiteUpdate.kind.contains(WebsiteKind.DEFAULT)) unvalidateOtherWebsites(website)
      else Future.successful(())

    updatedWebsite <- repository.update(websiteUpdate.mergeIn(website))
    maybeCompany <- website.companyId match {
      case Some(id) => companyRepository.fetchCompany(id)
      case None     => Future.successful(None)
    }
  } yield WebsiteCompany.toApi(updatedWebsite, maybeCompany)

  private[this] def unvalidateOtherWebsites(updatedWebsite: Website) =
    for {
      websitesWithSameHost <- repository
        .searchCompaniesByHost(updatedWebsite.host)
        .map(websites =>
          websites
            .map(_._1)
            .filter(_.id != updatedWebsite.id)
            .filter(_.kind == WebsiteKind.DEFAULT)
        )
      unvalidatedWebsites: Seq[Website] <-
        Future.sequence(
          websitesWithSameHost.map(website => repository.update(website.copy(kind = WebsiteKind.PENDING)))
        )
    } yield unvalidatedWebsites

}
