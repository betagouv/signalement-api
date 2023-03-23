package repositories.socialnetwork

import models.company.Company
import models.report.SocialNetworkSlug
import models.socialnetwork.SocialNetwork

import scala.concurrent.Future

trait SocialNetworkRepositoryInterface {
  def findCompanyBySocialNetworkSlug(slug: SocialNetworkSlug): Future[Option[Company]]
  def get(slug: SocialNetworkSlug): Future[Option[SocialNetwork]]
}
