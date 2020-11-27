package orchestrators

import javax.inject.Inject
import models._
import play.api.Logger
import play.api.libs.json.JsObject
import repositories._

import scala.concurrent.{ExecutionContext, Future}

class WebsiteOrchestrator @Inject()(
  companyRepository: CompanyRepository
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def toJsonWithCompany(website: Website): Future[Option[JsObject]] = {
    for {
      websitesJson <- toJsonWithCompany(Seq(website))
    } yield {
      websitesJson.headOption
    }
  }

  def toJsonWithCompany(websiteOpt: Option[Website]): Future[Option[JsObject]] = {
    websiteOpt.map(toJsonWithCompany).getOrElse(Future(None))
  }

  def toJsonWithCompany(websites: Seq[Website]): Future[Seq[JsObject]] = {
    for {
      companies <- companyRepository.fetchCompanies(websites.map(_.companyId).toList)
    } yield {
      val companiesGroupedById = companies.groupBy(_.id)

      def mapWebsiteWithCompanyToJson(website: Website) = {
        val company = companiesGroupedById.getOrElse(website.companyId, Seq()).headOption
        website.toJsonWithCompany(company)
      }

      websites.map(mapWebsiteWithCompanyToJson)
    }
  }
}
