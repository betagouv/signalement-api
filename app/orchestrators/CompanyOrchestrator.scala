package orchestrators

import config.TaskConfiguration
import controllers.CompanyObjects.CompanyList
import controllers.error.AppError.CompanyNotFound
import io.scalaland.chimney.dsl.TransformerOps
import models.company.SearchCompanyIdentity.SearchCompanyIdentityId
import models.event.Event.stringToDetailsJsValue
import models._
import models.company.Company
import models.company.CompanyAddressUpdate
import models.company.CompanyCreation
import models.company.CompanyRegisteredSearch
import models.company.CompanyWithNbReports
import models.company.UndeliveredDocument
import models.event.Event
import models.report.ReportFilter
import models.report.ReportStatus
import models.report.ReportTag
import models.website.WebsiteCompanySearchResult
import models.website.WebsiteHost
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import tasks.company.CompanySearchResult
import tasks.company.CompanySearchResult.fromCompany
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.SIRET
import utils.URL

import java.time.OffsetDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyOrchestrator(
    val companyRepository: CompanyRepositoryInterface,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    val reportRepository: ReportRepositoryInterface,
    val websiteRepository: WebsiteRepositoryInterface,
    val accessTokenRepository: AccessTokenRepositoryInterface,
    val eventRepository: EventRepositoryInterface,
    val taskConfiguration: TaskConfiguration
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  val reportReminderByPostDelay = Period.ofDays(28)

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
        .searchWithReportsCount(companyIdFilter, PaginatedSearch(None, None), user.userRole)

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
      .searchWithReportsCount(search, paginate, user.userRole)
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

  def getCompanyResponseRate(companyId: UUID, userRole: UserRole): Future[Int] = {

    val (tagFilter, statusFilter) = userRole match {
      case UserRole.Professionnel =>
        logger.debug("User is pro, filtering tag and status not visible by pro user")
        (ReportTag.ReportTagHiddenToProfessionnel, ReportStatus.statusVisibleByPro)
      case UserRole.Admin | UserRole.DGCCRF => (Seq.empty[ReportTag], Seq.empty[ReportStatus])
    }
    val responseReportsFilter =
      ReportFilter(
        companyIds = Seq(companyId),
        status = ReportStatus.statusWithProResponse,
        withoutTags = tagFilter
      )
    val totalReportsFilter =
      ReportFilter(companyIds = Seq(companyId), status = statusFilter, withoutTags = tagFilter)

    val totalReportsCount = reportRepository.count(totalReportsFilter)
    val responseReportsCount = reportRepository.count(responseReportsFilter)
    for {
      total <- totalReportsCount
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
      similarHosts = companiesByUrl
        .filterNot(x => URL(url).getHost.contains(x._1.host))
        .map(w => WebsiteHost(w._1.host))
        .distinct
        .take(3)
      exactMatch = companiesByUrl
        .filter(x => URL(url).getHost.contains(x._1.host))
        .map { case (website, company) =>
          fromCompany(company, website)
        }
      _ = logger.debug(s"Found exactMatch: $exactMatch, similarHosts: ${similarHosts}")
    } yield WebsiteCompanySearchResult(exactMatch, similarHosts)
  }

  def companiesToActivate(): Future[List[JsObject]] =
    for {
      tokensAndCompanies <- accessTokenRepository.companiesToActivate()
      pendingReports <- reportRepository.getPendingReports(tokensAndCompanies.map(_._2.id))
      eventsMap <- eventRepository.fetchEvents(tokensAndCompanies.map { case (_, c) => c.id })
    } yield tokensAndCompanies
      .map { case (accessToken, company) =>
        val companyEvents = eventsMap.getOrElse(company.id, Nil)
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
              OffsetDateTime.now.minus(
                reportReminderByPostDelay
                  .multipliedBy(Math.min(noticeCount, 3))
              )
            )
          )
        )
      }
      .map { case (company, token, _, lastNotice, _, _) =>
        Json.obj(
          "company" -> Json.toJson(company),
          "lastNotice" -> lastNotice,
          "tokenCreation" -> token.creationDate
        )
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
          .getOrElse(Future(None))
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
        .getOrElse(Future(None))
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
        .getOrElse(Future(None))
    } yield updatedCompany

  def handleUndeliveredDocument(
      siret: SIRET,
      identity: UUID,
      undeliveredDocument: UndeliveredDocument
  ): Future[Option[Event]] =
    for {
      company <- companyRepository.findBySiret(siret)
      event <- company
        .map(c =>
          eventRepository
            .create(
              Event(
                UUID.randomUUID(),
                None,
                Some(c.id),
                Some(identity),
                OffsetDateTime.now(),
                EventType.ADMIN,
                ActionEvent.ACTIVATION_DOC_RETURNED,
                stringToDetailsJsValue(s"Date de retour : ${undeliveredDocument.returnedDate
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}")
              )
            )
            .map(Some(_))
        )
        .getOrElse(Future(None))
    } yield event

}
