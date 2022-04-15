package repositories

import models.website.Website
import models.website.WebsiteKind
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import repositories.company.CompanyRepository
import utils.AppSpec
import utils.Fixtures

import scala.concurrent.Await
import scala.concurrent.duration._

class WebsiteRepositorySpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  lazy val companyRepository = injector.instanceOf[CompanyRepository]
  lazy val websiteRepository = injector.instanceOf[WebsiteRepository]

  val defaultCompany = Fixtures.genCompany.sample.get
  val marketplaceCompany = Fixtures.genCompany.sample.get
  val pendingCompany = Fixtures.genCompany.sample.get

  val defaultWebsite = Fixtures
    .genWebsite()
    .sample
    .get
    .copy(companyCountry = None, companyId = Some(defaultCompany.id), kind = WebsiteKind.DEFAULT)
  val marketplaceWebsite =
    Fixtures
      .genWebsite()
      .sample
      .get
      .copy(companyCountry = None, companyId = Some(marketplaceCompany.id), kind = WebsiteKind.MARKETPLACE)
  val pendingWebsite = Fixtures
    .genWebsite()
    .sample
    .get
    .copy(companyCountry = None, companyId = Some(pendingCompany.id), kind = WebsiteKind.PENDING)

  val newHost = Fixtures.genWebsiteURL.sample.get.getHost.get

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(defaultCompany.siret, defaultCompany)
        _ <- companyRepository.getOrCreate(marketplaceCompany.siret, marketplaceCompany)
        _ <- companyRepository.getOrCreate(pendingCompany.siret, pendingCompany)
        _ <- websiteRepository.create(defaultWebsite)
        _ <- websiteRepository.create(marketplaceWebsite)
        _ <- websiteRepository.create(pendingWebsite)
      } yield (),
      Duration.Inf
    )

  def is = s2"""

 This is a specification to check the WebsiteRepository

 Searching by URL should
    retrieve default website                                            $e1
    retrieve marketplace website                                        $e2
    not retrieve pending website                                        $e3

 Adding new website on company should
    if the website is already define for the company, return existing website       $e5
    else add new website with pending kind                                          $e7
 """

  def e1 = websiteRepository.searchCompaniesByUrl(s"http://${defaultWebsite.host}") must beEqualTo(
    Seq((defaultWebsite, defaultCompany))
  ).await
  def e2 = websiteRepository.searchCompaniesByUrl(
    s"http://${pendingWebsite.host}",
    Some(Seq(WebsiteKind.MARKETPLACE))
  ) must beEqualTo(Seq.empty).await
  def e3 = websiteRepository.searchCompaniesByUrl(s"http://${marketplaceWebsite.host}") must beEqualTo(
    Seq((marketplaceWebsite, marketplaceCompany))
  ).await
  def e5 = websiteRepository.create(
    Website(host = defaultWebsite.host, companyCountry = None, companyId = Some(defaultCompany.id))
  ) must beEqualTo(
    defaultWebsite
  ).await
  def e7 = {
    val newWebsite =
      websiteRepository.create(Website(host = newHost, companyCountry = None, companyId = Some(defaultCompany.id)))
    newWebsite
      .map(w => (w.host, w.companyId, w.kind)) must beEqualTo(
      (newHost, Some(defaultCompany.id), WebsiteKind.PENDING)
    ).await
  }
}
