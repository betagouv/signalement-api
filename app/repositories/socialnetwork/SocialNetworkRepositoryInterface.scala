package repositories.socialnetwork

import models.company.Company
import models.report.SocialNetworkSlug

import scala.concurrent.Future

trait SocialNetworkRepositoryInterface {
  def findCompanyBySocialNetworkSlug(slug: SocialNetworkSlug): Future[Option[Company]]
}
