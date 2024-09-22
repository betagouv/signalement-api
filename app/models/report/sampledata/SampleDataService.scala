package models.report.sampledata

import cats.implicits.toTraverseOps
import models.User
import models.UserRole.Professionnel
import models.company.AccessLevel
import models.report.IncomingReportResponse
import models.report.ReportFilter
import models.report.ReportResponseType.ACCEPTED
import models.report.sampledata.UserGenerator.generateSampleUser
import orchestrators.ReportAdminActionOrchestrator
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.ActorSystem
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.report.ReportRepositoryInterface
import repositories.user.UserRepositoryInterface
import repositories.website.WebsiteRepositoryInterface

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SampleDataService(
    companyRepository: CompanyRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    reportOrchestrator: ReportOrchestrator,
    reportRepository: ReportRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    reportAdminActionOrchestrator: ReportAdminActionOrchestrator,
    websiteRepository: WebsiteRepositoryInterface
)(implicit system: ActorSystem) {

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("io-dispatcher")

  def genSampleData() = {

    val proUser1 = generateSampleUser(
      UUID.fromString("de20b773-346a-4ff3-a42b-4defc1277c33"),
      "PRO 1",
      "PRO 1",
      "dev.signalconso+SAMPLE_PRO1@gmail.com",
      Professionnel
    )
    val proUser2 = generateSampleUser(
      UUID.fromString("88595273-6c9d-40b6-bb7c-a3a561cb4e27"),
      "PRO 2",
      "PRO 2",
      "dev.signalconso+SAMPLE_PRO2@gmail.com",
      Professionnel
    )
    val proUser3 = generateSampleUser(
      UUID.fromString("4f2ca273-1914-4815-af41-5b73e9b00a34"),
      "PRO 3",
      "PRO 3",
      "dev.signalconso+SAMPLE_PRO3@gmail.com",
      Professionnel
    )

    val groupCompanies = CompanyGenerator.groupCompany(3)

    for {
      _ <- delete(List(proUser1, proUser2, proUser3))
      _ <- createUser(proUser1)
      _ <- createUser(proUser2)
      _ <- createUser(proUser3)
      _ <- groupCompanies.flatTraverse(c =>
        for {
          _ <- companyRepository.create(c)
          _ <- accessTokenRepository.giveCompanyAccess(c, proUser1, AccessLevel.ADMIN)
          _ <- accessTokenRepository.giveCompanyAccess(c, proUser2, AccessLevel.MEMBER)
          _ <- accessTokenRepository.giveCompanyAccess(c, proUser3, AccessLevel.NONE)
          r = ReportGenerator.visibleReports(c, expired = false)
          _ <- r.traverse(reportOrchestrator.createReport)

          r2 = ReportGenerator.visibleReports(c, expired = false)
          createdReports <- r2.traverse(reportOrchestrator.createReport)
          response = IncomingReportResponse(ACCEPTED, "Consumer details", Some("CCRF details"), List.empty, None)
          _ <- createdReports.traverse(reportOrchestrator.handleReportResponse(_, response, proUser1))
        } yield createdReports
      )
    } yield ()

  }.recoverWith { case error =>
    println(s"------------------ error = ${error} ------------------")
    Future.successful(())
  }

  private def createUser(user: User) = {
    println(s"------------------ looking user = ${ user} ------------------")

    userRepository.findByEmail(user.email.value).foreach( x => println(s"------------------ _ = ${x} ------------------"))

    userRepository.get(user.id)
      .flatMap {
        case Some(_) =>
          println(s"------------------  = already aexist ------------------")
          Future.unit
        case None =>
          userRepository.create(user)
      }
      .flatMap(_ => userRepository.updatePassword(user.id, password = "test"))
  }

  private def delete(users: List[User]) = {
    println(s"------------------ users = ${users} ------------------")
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
        _ = println(s"------------------ reportList = ${reportList} ------------------")
        _ <- reportList.traverse(reportAdminActionOrchestrator.deleteReport)
        _ = println(s"------------------  = DELETED REPORTS ${reportList} ------------------")
        companies <- companyAccessRepository.fetchCompaniesWithLevel(u)
        websites  <- websiteRepository.searchByCompaniesId(companies.map(_.company.id))
        _         <- websites.map(_.id).traverse(websiteRepository.delete)
        _         <- companies.traverse(c => companyRepository.delete(c.company.id))
      } yield ()

    }
  }

}
