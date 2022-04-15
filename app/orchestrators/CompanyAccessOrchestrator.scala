package orchestrators

import cats.implicits.catsSyntaxOption
import controllers.error.AppError.ActivationCodeAlreadyUsed
import controllers.error.AppError.CompanyActivationCodeExpired
import controllers.error.AppError.CompanyActivationSiretOrCodeInvalid
import models.AccessLevel
import models.AccessToken
import models.access.ActivationLinkRequest
import play.api.Logger
import repositories.accesstoken.AccessTokenRepository
import repositories.company.CompanyRepository
import utils.SIRET

import java.time.OffsetDateTime.now
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyAccessOrchestrator @Inject(
) (
    val companyRepository: CompanyRepository,
    val accessTokenRepository: AccessTokenRepository,
    val accessesOrchestrator: AccessesOrchestrator
)(implicit val ec: ExecutionContext) {

  val logger = Logger(this.getClass)

  def sendActivationLink(siret: SIRET, activationLinkRequest: ActivationLinkRequest): Future[Unit] =
    for {
      company <- companyRepository
        .findBySiret(siret)
        .flatMap(maybeCompany =>
          maybeCompany.liftTo[Future] {
            logger.warn(s"Unable to activate company $siret, siret is unknown")
            CompanyActivationSiretOrCodeInvalid(siret)
          }
        )
      _ = logger.debug("Company found")
      token <-
        accessTokenRepository
          .fetchActivationToken(company.id)
          .flatMap(_.liftTo[Future] {
            logger.warn(s"No activation token found for siret $siret")
            CompanyActivationSiretOrCodeInvalid(siret)
          })
      _ = logger.debug("Token found")
      _ <- validateToken(token, activationLinkRequest, siret)
      _ = logger.debug("Token validated")
      _ <- accessesOrchestrator.addUserOrInvite(company, activationLinkRequest.email, AccessLevel.ADMIN, None)
    } yield ()

  def validateToken(
      accessToken: AccessToken,
      activationLinkRequest: ActivationLinkRequest,
      siret: SIRET
  ): Future[Unit] =
    if (activationLinkRequest.token != accessToken.token) {
      logger.warn(s"Unable to activate company $siret, code is not valid.")
      Future.failed(CompanyActivationSiretOrCodeInvalid(siret))
    } else if (!accessToken.valid) {
      logger.warn(s"Unable to activate company $siret, code has already been used.")
      Future.failed(ActivationCodeAlreadyUsed(activationLinkRequest.email))
    } else if (accessToken.expirationDate.exists(expiration => now isAfter expiration)) {
      logger.warn(s"Unable to activate company $siret, code has expired.")
      Future.failed(CompanyActivationCodeExpired(siret))
    } else Future.unit

}
