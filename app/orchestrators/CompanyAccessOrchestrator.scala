package orchestrators

import controllers.error.AppError.AlreadyActivatedCompany
import controllers.error.AppError.CompanyToActivateInvalidToken
import controllers.error.AppError.CompanyToActivateTokenOutdated
import models.Company
import repositories.AccessTokenRepository

import java.time.OffsetDateTime
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyAccessOrchestrator @Inject() (
    _accessToken: AccessTokenRepository
)(implicit val executionContext: ExecutionContext) {

  def checkCompanyInitToken(company: Company, token: String): Future[Boolean] = {
    val siret = company.siret.value
    for {
      tokenOpt <- _accessToken.fetchCompanyInitToken(company.id)
      notOutdatedToken <- tokenOpt
        .filter(_.expirationDate.exists(_.isAfter(OffsetDateTime.now)))
        .map(Future.successful)
        .getOrElse(Future.failed(CompanyToActivateTokenOutdated(siret)))
      validToken <-
        if (notOutdatedToken.token == token) Future(notOutdatedToken)
        else Future.failed(CompanyToActivateInvalidToken(siret))
      unusedToken <-
        if (notOutdatedToken.valid) Future(true)
        else Future.failed(AlreadyActivatedCompany(siret))
    } yield unusedToken
  }
}
