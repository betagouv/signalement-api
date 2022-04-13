package orchestrators

import config.TaskConfiguration
import controllers.CompanyObjects.CompanyList
import io.scalaland.chimney.dsl.TransformerOps
import models.event.Event.stringToDetailsJsValue
import models._
import models.event.Event
import models.report.ReportStatus
import models.report.ReportFilter
import models.website.WebsiteKind
import play.api.Logger
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import repositories.AccessTokenRepository
import repositories.CompanyDataRepository
import repositories.CompanyRepository
import repositories.EventRepository
import repositories.ReportRepository
import repositories.WebsiteRepository
import utils.Constants.ActionEvent
import utils.Constants.EventType
import utils.SIREN
import utils.SIRET

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyOrchestrator @Inject() (
    val companyRepository: CompanyRepository,
    val reportRepository: ReportRepository,
    val companyDataRepository: CompanyDataRepository,
    val websiteRepository: WebsiteRepository,
    val accessTokenRepository: AccessTokenRepository,
    val eventRepository: EventRepository,
    val taskConfiguration: TaskConfiguration
)(implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)

  def create(companyCreation: CompanyCreation): Future[Company] =
    companyRepository
      .getOrCreate(companyCreation.siret, companyCreation.toCompany())

  def fetchHosts(companyId: UUID): Future[Seq[String]] =
    reportRepository.getHostsByCompany(companyId)

  def searchRegistered(
      search: CompanyRegisteredSearch,
      paginate: PaginatedSearch
  ): Future[PaginatedResult[CompanyWithNbReports]] =
    companyRepository
      .searchWithReportsCount(search, paginate)
      .map(x =>
        x.copy(entities = x.entities.map { case (company, count, responseCount) =>
          val responseRate: Float = if (count > 0) (responseCount.toFloat / count) * 100 else 0f
          company
            .into[CompanyWithNbReports]
            .withFieldConst(_.count, count)
            .withFieldConst(_.responseRate, responseRate.round)
            .transform
        })
      )

  def getResponseRate(companyId: UUID): Future[Int] = {
    val totalF = reportRepository.count(ReportFilter(companyIds = Seq(companyId)))
    val responsesF = reportRepository.count(
      ReportFilter(companyIds = Seq(companyId), status = ReportStatus.ReportStatusProResponse)
    )
    for {
      total <- totalF
      responses <- responsesF
    } yield (responses.toFloat / total * 100).round
  }

  def searchCompany(q: String, postalCode: String): Future[List[CompanySearchResult]] = {
    logger.debug(s"searchCompany $postalCode $q")
    companyDataRepository
      .search(q, postalCode)
      .map(results => results.map(result => result._1.toSearchResult(result._2.map(_.label))))
  }

  def searchCompanyByIdentity(identity: String): Future[List[CompanySearchResult]] = {
    logger.debug(s"searchCompanyByIdentity $identity")

    (identity.replaceAll("\\s", "") match {
      case q if q.matches(SIRET.pattern) =>
        companyDataRepository.searchBySiretIncludingHeadOfficeWithActivity(SIRET.fromUnsafe(q))
      case q =>
        SIREN.pattern.r
          .findFirstIn(q)
          .map(siren =>
            for {
              headOffice <- companyDataRepository.searchHeadOfficeBySiren(SIREN(siren))
              companies <- headOffice
                .map(company => Future(List(company)))
                .getOrElse(companyDataRepository.searchBySiren(SIREN(siren)))
            } yield companies
          )
          .getOrElse(Future(List.empty))
    }).map(companiesWithActivity =>
      companiesWithActivity.map { case (company, activity) =>
        company.toSearchResult(activity.map(_.label))
      }
    )
  }

  def searchCompanyByWebsite(url: String): Future[Seq[CompanySearchResult]] = {
    logger.debug(s"searchCompaniesByHost $url")
    for {
      companiesByUrl <-
        websiteRepository.searchCompaniesByUrl(url, Some(Seq(WebsiteKind.DEFAULT, WebsiteKind.MARKETPLACE)))
      results <- Future.sequence(companiesByUrl.map { case (website, company) =>
        companyDataRepository
          .searchBySiret(company.siret)
          .map(_.map { case (company, activity) =>
            company.toSearchResult(activity.map(_.label), website.kind == WebsiteKind.MARKETPLACE)
          })
      })
    } yield results.flatten
  }

  def companyDetails(siret: SIRET): Future[Option[Company]] = companyRepository.findBySiret(siret)

  def companiesToActivate(): Future[List[JsObject]] =
    for {
      accesses <- accessTokenRepository.companiesToActivate()
      eventsMap <- eventRepository.fetchEvents(accesses.map { case (_, c) => c.id })
    } yield accesses
      .map { case (t, c) =>
        (
          c,
          t,
          eventsMap
            .get(c.id)
            .map(_.count(e => e.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC))
            .getOrElse(0),
          eventsMap
            .get(c.id)
            .flatMap(_.find(e => e.action == ActionEvent.POST_ACCOUNT_ACTIVATION_DOC))
            .map(_.creationDate),
          eventsMap
            .get(c.id)
            .flatMap(_.find(e => e.action == ActionEvent.ACTIVATION_DOC_REQUIRED))
            .map(_.creationDate)
        )
      }
      .filter { case (_, _, noticeCount, lastNotice, lastRequirement) =>
        !lastNotice.exists(
          _.isAfter(
            lastRequirement.getOrElse(
              OffsetDateTime.now.minus(
                taskConfiguration.report.reportReminderByPostDelay
                  .multipliedBy(Math.min(noticeCount, 3))
              )
            )
          )
        )
      }
      .map { case (c, t, _, lastNotice, _) =>
        Json.obj(
          "company" -> Json.toJson(c),
          "lastNotice" -> lastNotice,
          "tokenCreation" -> t.creationDate
        )
      }

  def confirmContactByPostOnCompanyList(companyList: CompanyList, identity: UUID): Future[List[Event]] =
    Future
      .sequence(companyList.companyIds.map { companyId =>
        eventRepository.createEvent(
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
      company <- companyRepository.fetchCompany(id)
      updatedCompany <-
        company
          .map(c => companyRepository.update(c.copy(address = companyAddressUpdate.address)).map(Some(_)))
          .getOrElse(Future(None))
      _ <- updatedCompany
        .filter(c => !company.map(_.address).contains(c.address))
        .map(c =>
          eventRepository.createEvent(
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
          eventRepository.createEvent(
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
            .createEvent(
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
