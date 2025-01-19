package models.report.sampledata

import cats.data.NonEmptyList
import cats.implicits.toTraverseOps
import models.User
import models.UserRole.Professionnel
import models.company.AccessLevel
import models.company.Company
import models.report.ExistingResponseDetails.REMBOURSEMENT_OU_AVOIR
import models.report.ConsumerIp
import models.report.IncomingReportResponse
import models.report.ReportResponseType.ACCEPTED
import models.report.ReportResponseType.NOT_CONCERNED
import models.report.ReportResponseType.REJECTED
import models.report.sampledata.UserGenerator.generateSampleUser
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

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

class SampleDataService(
    companyRepository: CompanyRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
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

  val proUserA = generateSampleUser(
    UUID.fromString("8a87f4f4-185a-47c4-a71d-7e27577d7483"),
    "André",
    "Andlard",
    "dev.signalconso+SAMPLE_PRO1@gmail.com",
    Professionnel
  )
  val proUserB = generateSampleUser(
    UUID.fromString("0920b263-223f-40ae-a5a1-9efe5b624966"),
    "Brigitte",
    "Briguenier",
    "dev.signalconso+SAMPLE_PRO2@gmail.com",
    Professionnel
  )
  val proUserC = generateSampleUser(
    UUID.fromString("3f80c538-676b-4f2b-a318-7625379e0040"),
    "Camille",
    "Camion",
    "dev.signalconso+SAMPLE_PRO3@gmail.com",
    Professionnel
  )

  val proUserD = generateSampleUser(
    UUID.fromString("6a17610b-2fb5-4d0e-b4b6-a700d1b446a7"),
    "Damien",
    "Damont",
    "dev.signalconso+SAMPLE_PRO4@gmail.com",
    Professionnel
  )

  val proUserE = generateSampleUser(
    UUID.fromString("b5dead94-d3ee-4718-a181-97dcb6c5b867"),
    "Élodie",
    "Elovirard",
    "dev.signalconso+SAMPLE_PRO5@gmail.com",
    Professionnel
  )

  val proUserF = generateSampleUser(
    UUID.fromString("d91520ec-b1d6-4163-9f70-ebd9117f06bc"),
    "François",
    "Français",
    "dev.signalconso+SAMPLE_PRO6@gmail.com",
    Professionnel
  )

  val consoIp = ConsumerIp("1.1.1.1")

  def genSampleData() = {

    val megacorpCompanies = CompanyGenerator.createMegacorpCompanyAndSubsidiaries(subsidiaryCount = 3)

    logger.info("BEGIN Sample service creation")
    for {
      _ <- deleteAllData(List(proUserA, proUserB, proUserC, proUserD, proUserE, proUserF))
      _ <- createUsers(List(proUserA, proUserB, proUserC, proUserD, proUserE, proUserF))
      _ <- createCompaniesWithReportsAndGiveAccess(megacorpCompanies, NonEmptyList.of(proUserA, proUserB))
      _ <- createCompaniesWithReportsAndGiveAccess(
        List(CompanyGenerator.createLoneCompany("COQUELICOT S.A.R.L")),
        NonEmptyList.one(proUserC)
      )
      _ <- createCompanyWithNoReports(CompanyGenerator.createLoneCompany("DELICE FRANCE"), proUserD)
      _ <- createCompaniesWithReportsAndGiveAccess(
        List(CompanyGenerator.createLoneCompany("FIFRELET")),
        NonEmptyList.of(proUserF, proUserE)
      )
    } yield ()

  }.recoverWith { case error =>
    logger.error("Error creating sample data", error)
    Future.successful(())
  }

  private def createCompanyWithNoReports(c: Company, proUser: User) = {
    logger.info(
      s"Creation of company ${c.id}, without reports, and accesses for ${proUser.firstName} "
    )
    for {
      _ <- companyRepository.create(c)
      _ <- accessTokenRepository.giveCompanyAccess(c, proUser, AccessLevel.ADMIN)
    } yield ()
  }

  private def createCompaniesWithReportsAndGiveAccess(
      groupCompanies: List[Company],
      proUsers: NonEmptyList[User]
  ): Future[List[Unit]] = {
    logger.info(
      s"Creation of companies ${groupCompanies.map(_.name).mkString(",")} and reports and accesses for ${proUsers.map(_.firstName).toList.mkString(", ")} "
    )
    groupCompanies.traverse(c =>
      for {
        _ <- companyRepository.create(c)
        _ = logger.info(
          s"--- Company created"
        )
        _ <- proUsers.traverse(accessTokenRepository.giveCompanyAccess(c, _, AccessLevel.ADMIN))
        _ = logger.info(
          s"--- Company access given to user"
        )
        reports = ReportGenerator.generateReportsForCompany(c)
        createdReports <- reports.traverse(reportOrchestrator.createReport(_, consoIp))
        _ = logger.info(
          s"--- Pending reports created"
        )
        _ <- createdReports.traverse { r =>
          val creationDate = OffsetDateTime.now().minusDays(Random.between(1L, 20L))
          reportRepository.update(
            r.id,
            r.copy(
              creationDate = creationDate,
              expirationDate = reportOrchestrator.chooseExpirationDate(creationDate, companyHasUsers = true)
            )
          )
        }
        _ = logger.info(
          s"--- Pending reports updated with new expiration date"
        )
        acceptedResponse = IncomingReportResponse(
          responseType = ACCEPTED,
          consumerDetails = "Consumer details",
          dgccrfDetails = Some("CCRF details"),
          fileIds = List.empty,
          responseDetails = Some(REMBOURSEMENT_OU_AVOIR)
        )
        rejectedResponse = IncomingReportResponse(
          REJECTED,
          "Consumer details",
          Some("CCRF details"),
          List.empty,
          None
        )
        notConcernedResponse = IncomingReportResponse(
          NOT_CONCERNED,
          "Consumer details",
          Some("CCRF details"),
          List.empty,
          None
        )
        _ = logger.info(
          s"--- Closed reports creation"
        )
        _ <- createProcessedReports(c, acceptedResponse, proUsers.head)
        _ = logger.info(
          s"--- accepted reports created"
        )
        _ <- createProcessedReports(c, rejectedResponse, proUsers.head)
        _ = logger.info(
          s"--- rejected reports created"
        )
        _ <- createProcessedReports(c, notConcernedResponse, proUsers.head)
        _ = logger.info(
          s"--- notConcerned reports created"
        )
        _ = logger.info(
          s"--- Closed reports created"
        )
      } yield ()
    )
  }
  private def createProcessedReports(c: Company, response: IncomingReportResponse, proUser: User) = for {
    createdReports <- ReportGenerator
      .generateReportsForCompany(c)
      .traverse(reportOrchestrator.createReport(_, consoIp))
    _ = logger.info(
      s"--- Closed reports created"
    )
    updateReports <- createdReports.traverse { r =>
      val creationDate = OffsetDateTime.now().minusWeeks(Random.between(1L, 101L))
      reportRepository.update(
        r.id,
        r.copy(
          creationDate = creationDate,
          expirationDate = reportOrchestrator.chooseExpirationDate(creationDate, companyHasUsers = true)
        )
      )
    }
    _ = logger.info(
      s"--- Closed reports expiration date updated"
    )
    _ <- updateReports.traverse(reportOrchestrator.handleReportResponse(_, response, proUser))
    _ = logger.info(
      s"--- Closed reports expiration pro response updated"
    )
  } yield ()

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

}
