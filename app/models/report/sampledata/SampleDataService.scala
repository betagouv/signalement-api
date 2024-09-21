package models.report.sampledata

import cats.implicits.toTraverseOps
import models.User
import models.UserRole.Professionnel
import models.company.AccessLevel
import models.report.IncomingReportResponse
import models.report.ReportResponseType.ACCEPTED
import models.report.sampledata.UserGenerator.generateSampleUser
import orchestrators.ReportOrchestrator
import org.apache.pekko.actor.ActorSystem
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.user.UserRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SampleDataService(
    companyRepository: CompanyRepositoryInterface,
    userRepository: UserRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    reportOrchestrator: ReportOrchestrator
)(implicit system: ActorSystem) {

  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("io-dispatcher")

  def genSampleData() = {

    val proUser1 = generateSampleUser("PRO 1", "PRO 1", "dev.signalconso+SAMPLE_PRO1@gmail.com", Professionnel)
    val proUser2 = generateSampleUser("PRO 2", "PRO 2", "dev.signalconso+SAMPLE_PRO2@gmail.com", Professionnel)
    val proUser3 = generateSampleUser("PRO 3", "PRO 3", "dev.signalconso+SAMPLE_PRO3@gmail.com", Professionnel)

    val groupCompanies = CompanyGenerator.groupCompany(3)

    for {
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

          _  = println(s"------------------  = AHAHAHAHAHAHAHAHAHAHAHHHAHAHAHAHAAHHAHAHAAHA ------------------")
          r2 = ReportGenerator.visibleReports(c, expired = false)
          _  = println(s"------------------  = BBBBBBBBBBBBBBBBBBBBBBBBB ------------------")
          createdReports <- r2.traverse(reportOrchestrator.createReport)
          _        = println(s"------------------  = ccccccccccccccccccccccccc ------------------")
          response = IncomingReportResponse(ACCEPTED, "Consumer details", Some("CCRF details"), List.empty, None)
          _ <- createdReports.traverse(reportOrchestrator.handleReportResponse(_, response, proUser1))
        } yield createdReports
      )
    } yield ()

  }.recoverWith { case error =>
    println(s"------------------ error = ${error} ------------------")
    Future.successful(())
  }

  private def createUser(user: User) = userRepository
    .create(user)
    .map(_ => userRepository.updatePassword(user.id, password = "test"))

}
