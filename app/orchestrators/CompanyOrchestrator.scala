package orchestrators

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import config.EmailConfiguration
import config.TaskConfiguration
import config.TokenConfiguration
import controllers.CompanyObjects.CompanyList
import controllers.error.AppError.CompanyNotFound
import io.scalaland.chimney.dsl._
import models.company.SearchCompanyIdentity.SearchCompanyIdentityId
import models.event.Event.stringToDetailsJsValue
import models._
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyAddressUpdate
import models.company.CompanyCreation
import models.company.CompanyRegisteredSearch
import models.company.CompanyWithNbReports
import models.company.InactiveCompany
import models.event.Event
import models.report.Report
import models.report.ReportFilter
import models.report.ReportStatus
import models.token.TokenKind.CompanyFollowUp
import models.website.WebsiteCompanySearchResult
import models.website.WebsiteHost
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.twirl.api.Html
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import services.PDFService
import tasks.company.CompanySearchResult
import tasks.company.CompanySearchResultApi
import tasks.company.CompanySearchResult.fromCompany
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.FrontRoute
import utils.URL

import java.time.OffsetDateTime
import java.time.Period
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps
import cats.syntax.traverse._

class CompanyOrchestrator(
    val companyRepository: CompanyRepositoryInterface,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    val reportRepository: ReportRepositoryInterface,
    val websiteRepository: WebsiteRepositoryInterface,
    val accessTokenRepository: AccessTokenRepositoryInterface,
    val eventRepository: EventRepositoryInterface,
    val pdfService: PDFService,
    val taskConfiguration: TaskConfiguration,
    val frontRoute: FrontRoute,
    val emailConfiguration: EmailConfiguration,
    val tokenConfiguration: TokenConfiguration
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  val reportReminderByPostDelay = Period.ofDays(28)

  val contactAddress = emailConfiguration.contactAddress

  def create(companyCreation: CompanyCreation): Future[Company] =
    companyRepository
      .getOrCreate(companyCreation.siret, companyCreation.toCompany())

  def fetchHosts(companyId: UUID): Future[Seq[String]] =
    reportRepository.getHostsByCompany(companyId)

  def searchRegisteredById(
      companyIdFilter: UUID,
      user: User
  ): Future[PaginatedResult[CompanyWithNbReports]] =
    for {
      visibleByUserCompanyIdFilter <- user.userRole match {
        case UserRole.Professionnel =>
          restrictCompanyIdFilterOnProVisibility(user, companyIdFilter)
        case _ => Future.successful(SearchCompanyIdentityId(companyIdFilter))
      }
      companyIdFilter = CompanyRegisteredSearch(identity = Some(visibleByUserCompanyIdFilter))
      paginatedResults <- companyRepository
        .searchWithReportsCount(companyIdFilter, PaginatedSearch(None, None), user)

      companiesWithNbReports = paginatedResults.entities.map { case (company, count, responseCount) =>
        toCompanyWithNbReports(company, count, responseCount)
      }
    } yield paginatedResults.copy(entities = companiesWithNbReports)

  def searchRegistered(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch,
      user: User
  ): Future[PaginatedResult[CompanyWithNbReports]] =
    companyRepository
      .searchWithReportsCount(search, paginate, user)
      .map(x =>
        x.copy(entities = x.entities.map { case (company, count, responseCount) =>
          toCompanyWithNbReports(company, count, responseCount)
        })
      )

  private def toCompanyWithNbReports(company: Company, count: Int, responseCount: Int) = {
    val responseRate: Float = if (count > 0) (responseCount.toFloat / count) * 100 else 0f
    company
      .into[CompanyWithNbReports]
      .withFieldConst(_.count, count)
      .withFieldConst(_.responseRate, responseRate.round)
      .transform
  }

  private def restrictCompanyIdFilterOnProVisibility(user: User, companyIdFilter: UUID) =
    companiesVisibilityOrchestrator
      .fetchVisibleCompanies(user)
      .map(_.map(_.company.id))
      .map { proVisibleCompanyIds =>
        if (proVisibleCompanyIds.contains(companyIdFilter)) {
          logger.debug(s"$companyIdFilter is visible by pro, allowing the filter ")
          SearchCompanyIdentityId(companyIdFilter)
        } else throw CompanyNotFound(companyIdFilter)
      }

  def getCompanyResponseRate(companyId: UUID, user: User): Future[Int] = {
    val responseReportsFilter =
      ReportFilter(companyIds = Seq(companyId), status = ReportStatus.statusWithProResponse)
    val totalReportsFilter =
      ReportFilter(companyIds = Seq(companyId))

    val totalReportsCount    = reportRepository.count(Some(user), totalReportsFilter)
    val responseReportsCount = reportRepository.count(Some(user), responseReportsFilter)
    for {
      total     <- totalReportsCount
      responses <- responseReportsCount
    } yield (responses.toFloat / total * 100).round
  }

  // TODO remove when front end will be updated
  def searchCompanyByWebsite(url: String): Future[Seq[CompanySearchResult]] = {
    logger.debug(s"searchCompaniesByHost $url")
    for {
      companiesByUrl <- websiteRepository.deprecatedSearchCompaniesByHost(url)
      _ = logger.debug(s"Found ${companiesByUrl.map(t => (t._1.host, t._2.siret, t._2.name))}")
      results = companiesByUrl.map { case (website, company) =>
        fromCompany(company, website)
      }
    } yield results
  }

  def searchSimilarCompanyByWebsite(url: String): Future[WebsiteCompanySearchResult] = {
    logger.debug(s"searchCompaniesByHost $url")
    for {
      companiesByUrl <- websiteRepository.searchCompaniesByUrl(url)
      (exact, similar) = companiesByUrl.partition(x => URL(url).getHost.contains(x._1.host))
      similarHosts = similar.distinct
        .take(3)
        .map(w => WebsiteHost(w._1.host))
      exactMatch = exact
        .map { case (website, company) =>
          CompanySearchResultApi.fromCompany(company, website)
        }
      _ = logger.debug(s"Found exactMatch: $exactMatch, similarHosts: ${similarHosts}")
    } yield WebsiteCompanySearchResult(exactMatch, similarHosts)
  }

  def companiesToActivate(): Future[List[JsObject]] =
    for {
      tokensAndCompanies <- accessTokenRepository.companiesToActivate()
      pendingReports     <- reportRepository.getPendingReports(tokensAndCompanies.map(_._2.id))
      eventsMap          <- eventRepository.fetchEvents(tokensAndCompanies.map { case (_, c) => c.id })
    } yield tokensAndCompanies
      .map { case (accessToken, company) =>
        val companyEvents     = eventsMap.getOrElse(company.id, Nil)
        val nbPreviousNotices = companyEvents.count(e => e.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC)
        val lastNoticeDate = companyEvents
          .find(_.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC)
          .map(_.creationDate)
        val lastRequirementOfNewNoticeDate = companyEvents
          .find(e => e.action == ActionEvent.ACTIVATION_DOC_REQUIRED)
          .map(_.creationDate)
        val nbCompanyPendingReports = pendingReports.count(_.companyId.exists(_ == company.id))
        (
          company,
          accessToken,
          nbPreviousNotices,
          lastNoticeDate,
          lastRequirementOfNewNoticeDate,
          nbCompanyPendingReports
        )
      }
      .filter { case (_, _, _, _, _, nbCompanyPendingReports) => nbCompanyPendingReports > 0 }
      .filter { case (_, _, noticeCount, lastNotice, lastRequirement, _) =>
        !lastNotice.exists(
          _.isAfter(
            lastRequirement.getOrElse(
              OffsetDateTime
                .now()
                .minus(
                  reportReminderByPostDelay
                    .multipliedBy(Math.min(noticeCount, 3))
                )
            )
          )
        )
      }
      .map { case (company, token, _, lastNotice, _, _) =>
        Json.obj(
          "company"       -> Json.toJson(company),
          "lastNotice"    -> lastNotice,
          "tokenCreation" -> token.creationDate
        )
      }

  def getInactiveCompanies =
    companyRepository.getInactiveCompanies.map(_.map { case (company, ignoredReportCount) =>
      InactiveCompany(company, ignoredReportCount)
    })

  def getFollowUpDocument(companyIds: List[UUID], userId: UUID): Future[Option[Source[ByteString, Unit]]] =
    for {
      companies      <- companyRepository.fetchCompanies(companyIds)
      followUpTokens <- companies.traverse(getFollowUpToken(_, userId))
      tokenMap = followUpTokens.collect { case token @ AccessToken(_, _, _, _, _, Some(companyId), _, _, _, _) =>
        (companyId, token)
      }.toMap
      htmlDocuments = companies.flatMap(company =>
        tokenMap
          .get(company.id)
          .map(activationKey =>
            views.html.pdfs.accountFollowUp(
              company,
              activationKey.token
            )(frontRoute = frontRoute, contactAddress = contactAddress)
          )
      )
    } yield
      if (htmlDocuments.nonEmpty) {
        Some(pdfService.createPdfSource(htmlDocuments))
      } else {
        None
      }

  private def getFollowUpToken(company: Company, userId: UUID): Future[AccessToken] =
    for {
      followUpTokens <- accessTokenRepository.fetchFollowUpToken(company.id)
      validToken = followUpTokens.find { t =>
        t.expirationDate.exists(_.isAfter(OffsetDateTime.now())) && t.valid
      }
      companyFollowUpToken <- validToken.fold {
        accessTokenRepository
          .create(
            AccessToken.build(
              kind = CompanyFollowUp,
              token = f"${Random.nextInt(1000000)}%06d",
              validity = tokenConfiguration.companyInitDuration,
              companyId = Some(company.id),
              level = Some(AccessLevel.ADMIN)
            )
          )
          .tap(_ =>
            eventRepository.create(
              Event(
                UUID.randomUUID(),
                None,
                Some(company.id),
                Some(userId),
                OffsetDateTime.now(),
                EventType.PRO,
                ActionEvent.POST_FOLLOW_UP_TOKEN_GEN
              )
            )
          )
      }(Future.successful(_))

    } yield companyFollowUpToken

  def getActivationDocument(companyIds: List[UUID]): Future[Option[Source[ByteString, Unit]]] =
    for {
      companies          <- companyRepository.fetchCompanies(companyIds)
      activationCodesMap <- accessTokenRepository.prefetchActivationCodes(companyIds)
      eventsMap          <- eventRepository.fetchEvents(companyIds)
      pendingReports     <- reportRepository.getPendingReports(companyIds)
    } yield {
      val pendingReportsMap = pendingReports.filter(_.companyId.isDefined).groupBy(_.companyId.get)
      val htmlDocuments = companies.flatMap(company =>
        activationCodesMap
          .get(company.id)
          .map(
            getHtmlDocumentForCompany(
              company,
              pendingReportsMap.getOrElse(company.id, Nil),
              eventsMap.getOrElse(company.id, Nil)
            )
          )
      )
      if (htmlDocuments.nonEmpty) {
        Some(pdfService.createPdfSource(htmlDocuments))
      } else {
        None
      }
    }

  private def getHtmlDocumentForCompany(company: Company, pendingReports: List[Report], events: List[Event])(
      activationKey: String
  ): Html = {
    val mailSentEvents = events
      .filter(_.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC)

    val report = pendingReports
      // just in case. Avoid communicating on past dates
      .filter(_.expirationDate.isAfter(OffsetDateTime.now()))
      .sortBy(_.expirationDate)
      .headOption
    val reportCreationLocalDate   = report.map(_.creationDate.toLocalDate)
    val reportExpirationLocalDate = report.map(_.expirationDate.toLocalDate)

    mailSentEvents match {
      case Nil =>
        views.html.pdfs.accountActivation(
          company,
          reportCreationLocalDate,
          reportExpirationLocalDate,
          activationKey
        )(frontRoute = frontRoute, contactAddress = contactAddress)
      case _ :: Nil =>
        views.html.pdfs.accountActivationReminder(
          company,
          reportCreationLocalDate,
          reportExpirationLocalDate,
          activationKey
        )(frontRoute = frontRoute, contactAddress = contactAddress)
      case _ =>
        views.html.pdfs.accountActivationLastReminder(
          company,
          reportNumber = pendingReports.length,
          reportCreationLocalDate,
          reportExpirationLocalDate,
          activationKey
        )(frontRoute = frontRoute, contactAddress = contactAddress)
    }
  }

  def confirmContactByPostOnCompanyList(companyList: CompanyList, identity: UUID): Future[List[Event]] =
    Future
      .sequence(companyList.companyIds.map { companyId =>
        eventRepository.create(
          Event(
            UUID.randomUUID(),
            None,
            Some(companyId),
            Some(identity),
            OffsetDateTime.now(),
            EventType.PRO,
            ActionEvent.POST_ACCOUNT_ACTIVATION_DOC
          )
        )
      })

  def confirmFollowUp(companyList: CompanyList, identity: UUID): Future[List[Event]] =
    Future
      .sequence(companyList.companyIds.map { companyId =>
        eventRepository.create(
          Event(
            UUID.randomUUID(),
            None,
            Some(companyId),
            Some(identity),
            OffsetDateTime.now(),
            EventType.PRO,
            ActionEvent.POST_FOLLOW_UP_DOC
          )
        )
      })

  def updateCompanyAddress(
      id: UUID,
      identity: UUID,
      companyAddressUpdate: CompanyAddressUpdate
  ): Future[Option[Company]] =
    for {
      company <- companyRepository.get(id)
      updatedCompany <-
        company
          .map(c => companyRepository.update(c.id, c.copy(address = companyAddressUpdate.address)).map(Some(_)))
          .getOrElse(Future.successful(None))
      _ <- updatedCompany
        .filter(c => !company.map(_.address).contains(c.address))
        .map(c =>
          eventRepository.create(
            Event(
              UUID.randomUUID(),
              None,
              Some(c.id),
              Some(identity),
              OffsetDateTime.now(),
              EventType.PRO,
              ActionEvent.COMPANY_ADDRESS_CHANGE,
              stringToDetailsJsValue(s"Addresse précédente : ${company.map(_.address).getOrElse("")}")
            )
          )
        )
        .getOrElse(Future.successful(None))
      _ <- updatedCompany
        .filter(_ => companyAddressUpdate.activationDocumentRequired)
        .map(c =>
          eventRepository.create(
            Event(
              UUID.randomUUID(),
              None,
              Some(c.id),
              Some(identity),
              OffsetDateTime.now(),
              EventType.PRO,
              ActionEvent.ACTIVATION_DOC_REQUIRED
            )
          )
        )
        .getOrElse(Future.successful(None))
    } yield updatedCompany

}
