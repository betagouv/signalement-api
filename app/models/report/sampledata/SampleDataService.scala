package models.report.sampledata

import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import controllers.error.AppError.ServerError
import models.User
import models.barcode.BarcodeProduct
import models.company.AccessLevel
import models.company.Company
import models.report.ConsumerIp
import models.report.IncomingReportResponse
import models.report.Report
import models.report.sampledata.ReponseGenerator.acceptedResponse
import models.report.sampledata.ReponseGenerator.notConcernedResponse
import models.report.sampledata.ReponseGenerator.rejectedResponse
import models.report.sampledata.UserGenerator.proUserA
import models.report.sampledata.UserGenerator.proUserB
import models.report.sampledata.UserGenerator.proUserC
import models.report.sampledata.UserGenerator.proUserD
import models.report.sampledata.UserGenerator.proUserE
import models.report.sampledata.UserGenerator.proUserF
import orchestrators.BarcodeOrchestrator
import orchestrators.ReportAdminActionOrchestrator
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.engagement.EngagementRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.user.UserRepositoryInterface
import repositories.website.WebsiteRepositoryInterface
import utils.FutureUtils.RichSeq

import java.time.OffsetDateTime
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
    websiteRepository: WebsiteRepositoryInterface,
    eventRepository: EventRepositoryInterface,
    engagementRepository: EngagementRepositoryInterface
)(implicit system: ActorSystem)
    extends Logging {

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("io-dispatcher")

  private val consoIp = ConsumerIp("1.1.1.1")

  def genSampleData() = {
    val megacorpCompanies = CompanyGenerator.createMegacorpCompanyAndSubsidiaries(subsidiaryCount = 3)
    logger.info("BEGIN Sample service creation")
    for {
      product <- createBarcodeProduct()
      _       <- deleteAllData(List(proUserA, proUserB, proUserC, proUserD, proUserE, proUserF))
      _       <- createUsers(List(proUserA, proUserB, proUserC, proUserD, proUserE, proUserF))
      _ <- createCompaniesWithReportsAndGiveAccess(
        megacorpCompanies,
        product,
        NonEmptyList.of(proUserA, proUserB),
        reportsAmountFactor = 4
      )
      _ <- createCompaniesWithReportsAndGiveAccess(
        List(CompanyGenerator.createLoneCompany("COQUELICOT S.A.R.L")),
        product,
        NonEmptyList.one(proUserC),
        reportsAmountFactor = 2
      )
      _ <- createCompanyWithNoReports(
        CompanyGenerator.createLoneCompany("DELICE VIDE FRANCE"),
        proUserD
      )
      _ <- createCompaniesWithReportsAndGiveAccess(
        List(CompanyGenerator.createLoneCompany("FIFRELET")),
        product,
        NonEmptyList.of(proUserF, proUserE)
      )
    } yield ()
  }.recoverWith { case error =>
    logger.error("Error creating sample data", error)
    Future.successful(())
  }

  private def createCompanyWithNoReports(c: Company, proUser: User) = {
    logger.info(s"Creation of company ${c.id} (without reports) and accesses for ${proUser.firstName} ")
    for {
      _ <- companyRepository.create(c)
      _ <- accessTokenRepository.giveCompanyAccess(c, proUser, AccessLevel.ADMIN)
    } yield ()
  }

  private def createCompaniesWithReportsAndGiveAccess(
      groupCompanies: List[Company],
      product: BarcodeProduct,
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
        _ <- createReports(c, product, reportsAmountFactor)
        _ = logger.info(s"--- Creating reports with response")
        _ <- createReportsWithResponse(c, product, reportsAmountFactor * 1.5, acceptedResponse(), respondant)
        _ <- createReportsWithResponse(c, product, reportsAmountFactor * 0.5, rejectedResponse(), respondant)
        _ <- createReportsWithResponse(c, product, reportsAmountFactor * 0.3, notConcernedResponse(), respondant)
        _ = logger.info(s"--- All done for company ${c.name}")
      } yield ()
    }
  }

  private def createReports(
      c: Company,
      barcodeProduct: BarcodeProduct,
      reportsAmountFactor: Double
  ): Future[List[Report]] =
    for {
      reports <- ReportGenerator
        .generateRandomNumberOfReports(c, barcodeProduct, reportsAmountFactor)
        .runSequentially(report => reportOrchestrator.createReport(report, consoIp))
      _ = logger.info(s"--- ${reports.length} reports created for ${c.name}")
      updatedReports <- reports.traverse(setCreationAndExpirationDate(_))
    } yield updatedReports

  private def createReportsWithResponse(
      c: Company,
      product: BarcodeProduct,
      reportsAmountFactor: Double,
      response: IncomingReportResponse,
      proUser: User
  ) = for {
    reports <- createReports(c, product, reportsAmountFactor)
    _       <- reports.traverse(r => reportOrchestrator.handleReportResponse(r, response, proUser))
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
            _ <- companies.traverse(c => companyRepository.delete(c.company.id))
            _ <- maybeUser.traverse(user => userRepository.hardDelete(user.id))
            _ = logger.info(s"Deletion done for company user ${predefinedUser.id}")
          } yield ()
        }
      _ = logger.info("DELETING previous data done")
    } yield ()

  }

  private def createBarcodeProduct(): Future[BarcodeProduct] = {
    val gtin = "3474341105842"
    for {
      maybeProduct <- barcodeOrchestrator.getByGTIN(gtin)
      product = maybeProduct.getOrElse(throw new ServerError(s"Couldn't find product $gtin for sample data"))
    } yield product
  }

}
