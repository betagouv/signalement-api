package models.report.sampledata

import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import models.User
import models.company.AccessLevel
import models.company.AccessLevel.ADMIN
import models.company.AccessLevel.MEMBER
import models.company.Company
import models.report._
import models.report.sampledata.CompanyGenerator.buildHeadOfficeAndThreeSubsidiaries
import models.report.sampledata.CompanyGenerator.buildLoneCompany
import models.report.sampledata.CompanyGenerator.getRandomSiren
import models.report.sampledata.ProUserGenerator._
import models.report.sampledata.ResponseGenerator.acceptedResponse
import models.report.sampledata.ResponseGenerator.notConcernedResponse
import models.report.sampledata.ResponseGenerator.rejectedResponse
import models.report.sampledata.ReportGenerator.SampleReportBlueprint
import models.report.sampledata.ReviewGenerator.randomConsumerReview
import models.report.sampledata.ReviewGenerator.randomEngagementReview
import orchestrators.BarcodeOrchestrator
import orchestrators.EngagementOrchestrator
import orchestrators.ReportAdminActionOrchestrator
import orchestrators.ReportConsumerReviewOrchestrator
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.engagement.EngagementRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.reportblockednotification.ReportNotificationBlockedRepositoryInterface
import repositories.user.UserRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import utils.FutureUtils.RichSeq

import java.time.OffsetDateTime
import java.util.Locale
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

class SampleDataService(
    companyRepository: CompanyRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
    barcodeOrchestrator: BarcodeOrchestrator,
    reportRepository: ReportRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    reportAdminActionOrchestrator: ReportAdminActionOrchestrator,
    reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
    engagementOrchestrator: EngagementOrchestrator,
    websiteRepository: WebsiteRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    engagementRepository: EngagementRepositoryInterface,
    reportNotificationBlockedRepository: ReportNotificationBlockedRepositoryInterface
)(implicit system: ActorSystem)
    extends Logging {

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("io-dispatcher")

  private val consoIp = ConsumerIp("1.1.1.1")

  def genSampleData() = {
    val megacorpCompanies =
      CompanyGenerator.buildMegacorpCompanyAndSubsidiaries()
    logger.info("BEGIN Sample service creation")
    for {
      _ <- deleteAllData(List(proUserA, proUserB, proUserC, proUserD, proUserE, proUserF, proUserJohnny))
      _ <- createUsers(List(proUserA, proUserB, proUserC, proUserD, proUserE, proUserF, proUserJohnny))
      _ <- createCompaniesWithReportsAndGiveAccess(
        megacorpCompanies,
        NonEmptyList.of(proUserA, proUserB),
        reportsAmountFactor = 4
      )
      _ <- createCompaniesWithReportsAndGiveAccess(
        List(CompanyGenerator.buildLoneCompany("COQUELICOT S.A.R.L")),
        NonEmptyList.one(proUserC),
        reportsAmountFactor = 2
      )
      _ <- createCompanyWithNoReports(
        CompanyGenerator.buildLoneCompany("DELICE VIDE FRANCE"),
        proUserD
      )
      _ <- createCompaniesWithReportsAndGiveAccess(
        List(CompanyGenerator.buildLoneCompany("FIFRELET")),
        NonEmptyList.of(proUserF, proUserE)
      )
      _ <- createEverythingForJohnny(proUserJohnny)
    } yield ()
  }

  private def createCompanyWithNoReports(c: Company, proUser: User) =
    for {
      _ <- createCompanyWithNoReportsAndConfigurableAccess(c, proUser, Some(AccessLevel.ADMIN))
    } yield ()

  private def createCompanyWithNoReportsAndConfigurableAccess(
      c: Company,
      proUser: User,
      accessLevel: Option[AccessLevel]
  ) = {
    logger.info(s"Creation of company ${c.id} (without reports) and accesses ${accessLevel} for ${proUser.firstName} ")
    for {
      _ <- companyRepository.create(c)
      _ <- accessLevel
        .map(level => accessTokenRepository.giveCompanyAccess(c, proUser, level))
        .getOrElse(Future.unit)
    } yield ()
  }

  private def createCompaniesWithReportsAndGiveAccess(
      groupCompanies: List[Company],
      proUsers: NonEmptyList[User],
      reportsAmountFactor: Double = 1
  ): Future[_] = {
    logger.info(
      s"--- Creation of companies ${groupCompanies.map(_.name).mkString(",")} and reports and accesses for ${proUsers.map(_.firstName).toList.mkString(", ")} "
    )
    val respondant = proUsers.head
    groupCompanies.runSequentially { c =>
      for {
        _ <- Future.successful(logger.info(s"--- Working on company ${c.name}"))
        _ <- companyRepository.create(c)
        _ = logger.info(s"--- Company ${c.name} created")
        _ <- proUsers.traverse(accessTokenRepository.giveCompanyAccess(c, _, AccessLevel.ADMIN))
        _ = logger.info(s"--- Company access given to user")
        _ = logger.info(s"--- Creating reports without response")
        _ <- createReports(c, reportsAmountFactor)
        _ = logger.info(s"--- Creating reports with response")
        _ <- createReportsWithResponse(c, reportsAmountFactor * 0.5, rejectedResponse(), respondant)
        _ <- createReportsWithResponse(c, reportsAmountFactor * 0.3, notConcernedResponse(), respondant)
        reportsWithAcceptedResponse <- createReportsWithResponse(
          c,
          reportsAmountFactor * 1.5,
          acceptedResponse(),
          respondant
        )
        _ <- addReviews(reportsWithAcceptedResponse)
        _ = logger.info(s"--- All done for company ${c.name}")
      } yield ()
    }
  }

  private def createEverythingForJohnny(johnny: User): Future[Unit] = {
    // Johnny is a special user used to test all combinations of companies accesses, admin/member with headoffices/subsidiaries etc.
    val (jupiterHeadOffice, jupiterSubsidiary1, jupiterSubsidiary2, jupiterSubsidiary3) =
      buildHeadOfficeAndThreeSubsidiaries("Jupiter", getRandomSiren)
    val (jazzHeadOffice, jazzSubsidiary1, jazzSubsidiary2, jazzSubsidiary3) =
      buildHeadOfficeAndThreeSubsidiaries("Jazz", getRandomSiren)

    val loneHeadOffice = buildLoneCompany("Journal", isHeadOffice = true)
    val loneSubsidiary = buildLoneCompany("Jardin", isHeadOffice = false)
    val create         = createCompanyWithNoReportsAndConfigurableAccess _
    for {
      _ <- create(jupiterHeadOffice, johnny, Some(ADMIN))
      _ <- create(jupiterSubsidiary1, johnny, Some(ADMIN))
      _ <- create(jupiterSubsidiary2, johnny, Some(MEMBER))
      _ <- create(jupiterSubsidiary3, johnny, None)
      _ <- create(jazzHeadOffice, johnny, Some(MEMBER))
      _ <- create(jazzSubsidiary1, johnny, Some(ADMIN))
      _ <- create(jazzSubsidiary2, johnny, Some(MEMBER))
      _ <- create(jazzSubsidiary3, johnny, None)
      _ <- create(loneHeadOffice, johnny, Some(ADMIN))
      _ <- create(loneSubsidiary, johnny, Some(MEMBER))
    } yield ()
  }

  private def createReports(
      c: Company,
      reportsAmountFactor: Double
  ): Future[List[Report]] =
    for {
      reportsDrafts <- ReportGenerator
        .generateRandomNumberOfReports(reportsAmountFactor)
        .runSequentially(buildDraft(c, _))
      reports <- reportsDrafts
        .runSequentially(r => reportOrchestrator.createReport(r, consoIp))
      _ = logger.info(s"--- ${reports.length} reports created for ${c.name}")
      updatedReports <- reports.traverse(setCreationAndExpirationDate(_))
    } yield updatedReports

  private def createReportsWithResponse(
      c: Company,
      reportsAmountFactor: Double,
      response: IncomingReportResponse,
      proUser: User
  ) = for {
    reports <- createReports(c, reportsAmountFactor)
    _       <- reports.traverse(r => reportOrchestrator.handleReportResponse(r, response, proUser))
  } yield reports

  private def addReviews(reports: List[Report]): Future[_] =
    for {
      _ <- reports.filter(_ => Random.nextDouble() > 0.6).runSequentially { r =>
        reportConsumerReviewOrchestrator.handleReviewOnReportResponse(r.id, randomConsumerReview())
      }
      _ <- reports.filter(_ => Random.nextDouble() > 0.2).runSequentially { r =>
        engagementOrchestrator.handleEngagementReview(r.id, randomEngagementReview())
      }
    } yield ()

  private def setCreationAndExpirationDate(r: Report, quiteOld: Boolean = false): Future[Report] = {
    val now = OffsetDateTime.now()
    val creationDate =
      if (quiteOld) now.minusWeeks(Random.between(1L, 101L))
      else now.minusDays(Random.between(1L, 20L))
    reportRepository.update(
      r.id,
      r.copy(
        creationDate = creationDate,
        expirationDate = reportOrchestrator.chooseExpirationDate(creationDate, companyHasUsers = true)
      )
    )
  }

  private def createUsers(users: Seq[User]) =
    users.traverse(createUser)

  private def createUser(user: User) = {
    logger.info(s"Creation pro user ${user.firstName} ${user.email}")
    userRepository
      .findByIds(List(user.id))
      .map(_.headOption)
      .flatMap {
        case Some(_) =>
          Future.unit
        case None =>
          userRepository.create(user)
      }
      .flatMap(_ => userRepository.updatePassword(user.id, password = "test"))
  }

  private def deleteAllData(predefinedUsers: List[User]) = {
    logger.info("DELETING previous data")
    for {
      _ <- predefinedUsers
        .traverse { predefinedUser =>
          for {
            maybeUser <- userRepository.get(predefinedUser.id)
            _ = logger.info(s"Looking for ${predefinedUser.id}, existing ?: ${maybeUser.isDefined}")
            maybeCompany <- maybeUser.traverse(user => companyAccessRepository.fetchCompaniesWithLevel(user))
            companies = maybeCompany.getOrElse(List.empty)
            _ = logger.info(
              s"Looking for companies link to company user ${predefinedUser.id} , found: ${companies.size}"
            )
            companyIds = companies.map(c => c.company.id)
            reportList <- companyIds.flatTraverse(c => reportRepository.getReports(c))
            _ = logger.info(s"Looking for reports link to company user ${predefinedUser.id}, found: ${reportList.size}")
            _ <- reportList.traverse(r => reportAdminActionOrchestrator.deleteReport(r.id))
            _ <- maybeUser.traverse { user =>
              engagementRepository.removeByUserId(user.id).flatMap(_ => eventRepository.deleteByUserId(user.id))
            }
            websites <- websiteRepository.searchByCompaniesId(companies.map(_.company.id))
            _ = logger.info(
              s"Looking for websites link to company user ${predefinedUser.id}, found: ${reportList.size}"
            )
            _ <- websites.map(_.id).traverse(websiteRepository.delete)
            _ <- maybeUser.traverse(user => reportNotificationBlockedRepository.delete(user.id, companyIds))
            _ <- companies.traverse(c => companyRepository.delete(c.company.id))
            _ <- maybeUser.traverse(user => userRepository.hardDelete(user.id))
            _ = logger.info(
              s"Deletion done for company user ${predefinedUser.firstName} ${predefinedUser.lastName} ${predefinedUser.id}"
            )
          } yield ()
        }
      _ = logger.info("DELETING previous data done")
    } yield ()

  }

  private def buildDraft(company: Company, report: SampleReportBlueprint): Future[ReportDraft] = {
    val c     = company
    val r     = report
    val conso = r.conso

    for {
      maybeBarcodeProductId <- report.barcodeProductGtin
        .map(barcodeOrchestrator.getByGTIN)
        .getOrElse(Future.successful(None))
      reportDraft = ReportDraft(
        gender = conso.gender,
        category = r.category.entryName,
        subcategories = r.subcategories,
        details = r.details.map { case (k, v) => DetailInputValue(k, v) }.toList,
        influencer = None,
        companyName = Some(c.name),
        companyCommercialName = c.commercialName,
        companyEstablishmentCommercialName = c.establishmentCommercialName,
        companyBrand = c.brand,
        companyAddress = Some(c.address),
        companySiret = Some(c.siret),
        companyActivityCode = c.activityCode,
        companyIsHeadOffice = Some(c.isHeadOffice),
        companyIsOpen = Some(c.isOpen),
        companyIsPublic = Some(c.isPublic),
        websiteURL = r.website,
        phone = r.phone,
        firstName = conso.firstName,
        lastName = conso.lastName,
        email = conso.email,
        contactAgreement = conso.contactAgreement,
        consumerPhone = conso.phone,
        consumerReferenceNumber = None,
        employeeConsumer = r.employeeConsumer,
        forwardToReponseConso = Some(r.tags.contains(ReportTag.ReponseConso)),
        fileIds = List.empty,
        vendor = None,
        tags = r.tags,
        reponseconsoCode = None,
        ccrfCode = None,
        lang = Some(Locale.FRENCH),
        barcodeProductId = maybeBarcodeProductId.map(_.id),
        metadata = None,
        train = None,
        station = None,
        rappelConsoId = None
      )
    } yield reportDraft

  }

}
