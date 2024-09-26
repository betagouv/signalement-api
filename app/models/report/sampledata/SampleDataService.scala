package models.report.sampledata

import cats.data.NonEmptyList
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

    for {
      _ <- delete(List(proUser1, proUser2, proUser3, proUser4, proUser5))
      _ <- createUser(proUser1)
      _ <- createUser(proUser2)
      _ <- createUser(proUser3)
      _ <- createUser(proUser4)
      _ <- createUser(proUser5)
      _ <- createUser(proUser6)
      _ <- createGroupCompanyReport(groupCompanies, NonEmptyList.of(proUser1, proUser2))
      _ <- createGroupCompanyReport(List(CompanyGenerator.createCompany), NonEmptyList.one(proUser3))
      _ <- createCompanyWithNoReports(CompanyGenerator.createCompany, proUser4)
      _ <- createGroupCompanyReport(List(CompanyGenerator.createCompany), NonEmptyList.of(proUser6, proUser5))
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
        _ <- proUsers.traverse(accessTokenRepository.giveCompanyAccess(c, _, AccessLevel.ADMIN))
        reports = ReportGenerator.visibleReports(c, expired = false)
        createdReports <- reports.traverse(reportOrchestrator.createReport)
        _ <- createdReports.traverse { r =>
          val creationDate = OffsetDateTime.now().minusDays(Random.between(1, 20))
          reportRepository.update(
            r.id,
            r.copy(
              creationDate = creationDate,
              expirationDate = reportOrchestrator.chooseExpirationDate(creationDate, companyHasUsers = true)
            )
          )
        }
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
        _ <- processedReports(c, acceptedResponse, proUsers.head)
        _ <- processedReports(c, rejectedResponse, proUsers.head)
        _ <- processedReports(c, notConcernedResponse, proUsers.head)
      } yield ()
    )

  private def processedReports(c: Company, response: IncomingReportResponse, proUser: User) = for {
    createdReports <- ReportGenerator.visibleReports(c, expired = false).traverse(reportOrchestrator.createReport)

    updateReports <- createdReports.traverse { r =>
      val creationDate = OffsetDateTime.now().minusWeeks(Random.between(1, 101))
      reportRepository.update(
        r.id,
        r.copy(
          creationDate = creationDate,
          expirationDate = reportOrchestrator.chooseExpirationDate(creationDate, companyHasUsers = true)
        )
      )
    }
    _ <- updateReports.traverse(reportOrchestrator.handleReportResponse(_, response, proUser))
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

  private def delete(users: List[User]) =
    users.traverse { u =>
      for {
        companies <- companyAccessRepository.fetchCompaniesWithLevel(u)
        sirets = companies.map(_.company.siret.value)
        reportList <- reportRepository
          .getReports(
            None,
            ReportFilter(siretSirenList = sirets, siretSirenDefined = Some(true)),
            None,
            None
          )
          .map(_.entities.map(_.report.id))
        _         <- reportList.traverse(reportAdminActionOrchestrator.deleteReport)
        companies <- companyAccessRepository.fetchCompaniesWithLevel(u)
        websites  <- websiteRepository.searchByCompaniesId(companies.map(_.company.id))
        _         <- websites.map(_.id).traverse(websiteRepository.delete)
        _         <- companies.traverse(c => companyRepository.delete(c.company.id))
        _         <- userRepository.hardDelete(u.id)
      } yield ()
    }

}
