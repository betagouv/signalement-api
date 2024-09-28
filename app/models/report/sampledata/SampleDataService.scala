package models.report.sampledata

import cats.data.NonEmptyList
import cats.implicits.toFlatMapOps
import cats.implicits.toTraverseOps
import models.User
import models.UserRole.Professionnel
import models.company.AccessLevel
import models.company.Company
import models.report.ReportResponseType.ACCEPTED
import models.report.ReportResponseType.NOT_CONCERNED
import models.report.ReportResponseType.REJECTED
import models.report.sampledata.UserGenerator.generateSampleUser
import models.report.IncomingReportResponse
import models.report.ReportFilter
import orchestrators.ReportAdminActionOrchestrator
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.ActorSystem
import play.api.Logging
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.user.UserRepositoryInterface
import repositories.website.WebsiteRepositoryInterface

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random
import scala.util.chaining.scalaUtilChainingOps

class SampleDataService(
    companyRepository: CompanyRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
    reportRepository: ReportRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    reportAdminActionOrchestrator: ReportAdminActionOrchestrator,
    websiteRepository: WebsiteRepositoryInterface
)(implicit system: ActorSystem)
    extends Logging {

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("io-dispatcher")

  def genSampleData() = {

    val proUser1 = generateSampleUser(
      UUID.fromString("8a87f4f4-185a-47c4-a71d-7e27577d7483"),
      "User",
      "PRO 1",
      "dev.signalconso+SAMPLE_PRO1@gmail.com",
      Professionnel
    )
    val proUser2 = generateSampleUser(
      UUID.fromString("0920b263-223f-40ae-a5a1-9efe5b624966"),
      "User",
      "PRO 2",
      "dev.signalconso+SAMPLE_PRO2@gmail.com",
      Professionnel
    )
    val proUser3 = generateSampleUser(
      UUID.fromString("3f80c538-676b-4f2b-a318-7625379e0040"),
      "User",
      "PRO 3",
      "dev.signalconso+SAMPLE_PRO3@gmail.com",
      Professionnel
    )

    val proUser4 = generateSampleUser(
      UUID.fromString("6a17610b-2fb5-4d0e-b4b6-a700d1b446a7"),
      "User",
      "PRO 4",
      "dev.signalconso+SAMPLE_PRO4@gmail.com",
      Professionnel
    )

    val proUser5 = generateSampleUser(
      UUID.fromString("b5dead94-d3ee-4718-a181-97dcb6c5b867"),
      "User",
      "PRO 5",
      "dev.signalconso+SAMPLE_PRO5@gmail.com",
      Professionnel
    )

    val proUser6 = generateSampleUser(
      UUID.fromString("d91520ec-b1d6-4163-9f70-ebd9117f06bc"),
      "User",
      "PRO 6",
      "dev.signalconso+SAMPLE_PRO6@gmail.com",
      Professionnel
    )

    val groupCompanies = CompanyGenerator.createCompanies(subsidiaryCount = 3)

    logger.info("BEGIN Sample service creation")
    for {
      _ <- delete(List(proUser1, proUser2, proUser3, proUser4, proUser5))
      _ = logger.info(s"Creation proUser1  ${proUser1.id}")
      _ <- createUser(proUser1)
      _ = logger.info(s"Creation proUser2  ${proUser2.id}")
      _ <- createUser(proUser2)
      _ = logger.info(s"Creation proUser3  ${proUser3.id}")
      _ <- createUser(proUser3)
      _ = logger.info(s"Creation proUser4  ${proUser4.id}")
      _ <- createUser(proUser4)
      _ = logger.info(s"Creation proUser5  ${proUser5.id}")
      _ <- createUser(proUser5)
      _ = logger.info(s"Creation proUser6  ${proUser6.id}")
      _ <- createUser(proUser6)
      _ = logger.info(
        s"Creation of company and report for proUser1, proUser2  for companies ${groupCompanies.map(_.id).mkString(",")}"
      )
      _ <- createGroupCompanyReport(groupCompanies, NonEmptyList.of(proUser1, proUser2))
      proUser3Company = CompanyGenerator.createCompany
      _ = logger.info(
        s"Creation of company and report for proUser3  for companies ${proUser3Company.id}"
      )
      _ <- createGroupCompanyReport(List(proUser3Company), NonEmptyList.one(proUser3))
      proUser4Company = CompanyGenerator.createCompany
      _ = logger.info(
        s"Creation of company and report for proUser4  for companies ${proUser4Company.id}"
      )
      _ <- createCompanyWithNoReports(proUser4Company, proUser4)
      proUser5Company = CompanyGenerator.createCompany
      _ = logger.info(
        s"Creation of company and report for proUser5  for companies ${proUser5Company.id}"
      )
      _ <- createGroupCompanyReport(List(proUser5Company), NonEmptyList.of(proUser6, proUser5))
    } yield ()

  }.recoverWith { case error =>
    logger.error("Error creating sample data", error)
    Future.successful(())
  }

  private def createCompanyWithNoReports(c: Company, proUser: User) =
    for {
      _ <- companyRepository.create(c)
      _ <- accessTokenRepository.giveCompanyAccess(c, proUser, AccessLevel.ADMIN)
    } yield ()

  private def createGroupCompanyReport(
      groupCompanies: List[Company],
      proUsers: NonEmptyList[User]
  ): Future[List[Unit]] =
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
        reports = ReportGenerator.visibleReports(c)
        createdReports <- reports.traverse(reportOrchestrator.createReport)
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
          responseDetails = None
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
        _ <- processedReports(c, acceptedResponse, proUsers.head)
        _ = logger.info(
          s"--- accepted reports created"
        )
        _ <- processedReports(c, rejectedResponse, proUsers.head)
        _ = logger.info(
          s"--- rejected reports created"
        )
        _ <- processedReports(c, notConcernedResponse, proUsers.head)
        _ = logger.info(
          s"--- notConcerned reports created"
        )
        _ = logger.info(
          s"--- Closed reports created"
        )
      } yield ()
    )

  private def processedReports(c: Company, response: IncomingReportResponse, proUser: User) = for {
    createdReports <- ReportGenerator.visibleReports(c).traverse(reportOrchestrator.createReport)
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

  private def createUser(user: User) =
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

  private def delete(predefinedUsers: List[User]) = {
    logger.info("DELETING previous data")
    val o = predefinedUsers
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
          reportList <- reportRepository
            .getReports(
              None,
              ReportFilter(companyIds = companyIds),
              None,
              None
            )
            .map(_.entities.map(_.report.id))
          _ = logger.info(s"Looking for reports link to company user ${predefinedUser.id}, found: ${reportList.size}")
          _ <- reportList.traverse(reportAdminActionOrchestrator.deleteReport)

          websites <- websiteRepository.searchByCompaniesId(companies.map(_.company.id))
          _ = logger.info(s"Looking for websites link to company user ${predefinedUser.id}, found: ${reportList.size}")
          _ <- websites.map(_.id).traverse(websiteRepository.delete)
          _ <- companies.traverse(c => companyRepository.delete(c.company.id))
          _ <- maybeUser.traverse(user => userRepository.hardDelete(user.id))
          _ = logger.info(s"Deletion done for company user ${predefinedUser.id}")
        } yield ()
      }
    o.tap(_ => logger.info("DELETING previous data Done"))

  }

}
