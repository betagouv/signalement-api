package repositories

import models.website.IdentificationStatus
import models.website.Website
import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import scala.concurrent.Await
import scala.concurrent.duration._

class WebsiteRepositorySpec(implicit ee: ExecutionEnv) extends Specification with AppSpec with FutureMatchers {

  val (app, components) = TestApp.buildApp(
    None
  )

  lazy val companyRepository = components.companyRepository
  lazy val websiteRepository = components.websiteRepository

  val defaultCompany     = Fixtures.genCompany.sample.get
  val marketplaceCompany = Fixtures.genCompany.sample.get
  val pendingCompany     = Fixtures.genCompany.sample.get

  val defaultWebsite = Fixtures
    .genWebsite()
    .sample
    .get
    .copy(
      companyCountry = None,
      companyId = Some(defaultCompany.id),
      identificationStatus = IdentificationStatus.Identified
    )

  val (similarHost1, similarHost2) = ("similar1.com", "similar2.com")

  val similarWebsite = Fixtures
    .genWebsite()
    .sample
    .get
    .copy(
      host = similarHost1,
      companyCountry = None,
      companyId = Some(defaultCompany.id),
      identificationStatus = IdentificationStatus.Identified
    )

  val marketplaceWebsite =
    Fixtures
      .genWebsite()
      .sample
      .get
      .copy(
        companyCountry = None,
        companyId = Some(marketplaceCompany.id),
        identificationStatus = IdentificationStatus.Identified,
        isMarketplace = true
      )

  val pendingWebsite = Fixtures
    .genWebsite()
    .sample
    .get
    .copy(
      companyCountry = None,
      companyId = Some(pendingCompany.id),
      identificationStatus = IdentificationStatus.NotIdentified
    )

  val newHost = Fixtures.genWebsiteURL.sample.get.getHost.get

  override def setupData() =
    Await.result(
      for {
        _ <- companyRepository.getOrCreate(defaultCompany.siret, defaultCompany)
        _ <- companyRepository.getOrCreate(marketplaceCompany.siret, marketplaceCompany)
        _ <- companyRepository.getOrCreate(pendingCompany.siret, pendingCompany)
        _ <- websiteRepository.validateAndCreate(defaultWebsite)
        _ <- websiteRepository.validateAndCreate(marketplaceWebsite)
        _ <- websiteRepository.validateAndCreate(pendingWebsite)
        _ <- websiteRepository.validateAndCreate(similarWebsite)
      } yield (),
      Duration.Inf
    )

  def is = s2"""

 This is a specification to check the WebsiteRepositoryInterface

 Searching by URL should
    retrieve default website                                            $e1
    not retrieve pending website                                        $e2
    retrieve marketplace website                                        $e3
    retrieve similar website                                            $e4

 Adding new website on company should
    if the website is already define for the company, return existing website       $e5
    else add new website with pending kind                                          $e7
 """

  def e1 = websiteRepository.searchCompaniesByUrl(s"http://${defaultWebsite.host}", 3) must beEqualTo(
    Seq(((defaultWebsite, 0), defaultCompany))
  ).await
  def e2 = websiteRepository.searchCompaniesByUrl(
    s"http://${pendingWebsite.host}",
    3
  ) must beEqualTo(Seq.empty).await

  def e3 = websiteRepository.searchCompaniesByUrl(s"http://${marketplaceWebsite.host}", 3) must beEqualTo(
    Seq(((marketplaceWebsite, 0), marketplaceCompany))
  ).await

  def e4 = websiteRepository.searchCompaniesByUrl(s"http://${similarHost2}", 3) must beEqualTo(
    Seq(((similarWebsite, 1), defaultCompany))
  ).await

  def e5 = websiteRepository.validateAndCreate(
    Website(
      host = defaultWebsite.host,
      creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
      companyCountry = None,
      companyId = Some(defaultCompany.id),
      lastUpdated = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
    )
  ) must beEqualTo(
    defaultWebsite
  ).await
  def e7 = {
    val newWebsite =
      websiteRepository.validateAndCreate(
        Website(
          host = newHost,
          creationDate = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS),
          companyCountry = None,
          companyId = Some(defaultCompany.id),
          lastUpdated = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        )
      )
    newWebsite
      .map(w => (w.host, w.companyId, w.identificationStatus)) must beEqualTo(
      (newHost, Some(defaultCompany.id), IdentificationStatus.NotIdentified)
    ).await
  }
}
