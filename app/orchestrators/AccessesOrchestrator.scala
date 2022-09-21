package orchestrators

import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.EmailConfiguration
import config.TokenConfiguration
import controllers.error.AppError._
import io.scalaland.chimney.dsl.TransformerOps
import models._
import models.token.AdminOrDgccrfTokenKind
import models.token.DGCCRFAccessToken
import models.token.DGCCRFUserActivationToken
import models.token.TokenKind
import models.token.TokenKind.AdminAccount
import models.token.TokenKind.DGCCRFAccount
import models.token.TokenKind.ValidateEmail
import play.api.Logger
import repositories.accesstoken.AccessTokenRepositoryInterface
import services.Email.AdminAccessLink
import services.Email.DgccrfAccessLink
import services.Email
import services.MailServiceInterface
import utils.EmailAddress
import utils.FrontRoute

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class AccessesOrchestrator(
    userOrchestrator: UserOrchestratorInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    mailService: MailServiceInterface,
    frontRoute: FrontRoute,
    emailConfiguration: EmailConfiguration,
    tokenConfiguration: TokenConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)
  implicit val timeout: akka.util.Timeout = 5.seconds

  def listDGCCRFPendingToken(user: User): Future[List[DGCCRFAccessToken]] =
    for {
      tokens <- accessTokenRepository.fetchPendingTokensDGCCRF
    } yield tokens
      .map { token =>
        DGCCRFAccessToken(token.creationDate, token.token, token.emailedTo, token.expirationDate, user.userRole)
      }

  def activateAdminOrDGCCRFUser(draftUser: DraftUser, token: String) = for {
    maybeAccessToken <- accessTokenRepository.findToken(token)
    (accessToken, userRole) <- maybeAccessToken
      .collect {
        case t if t.kind == TokenKind.DGCCRFAccount => (t, UserRole.DGCCRF)
        case t if t.kind == TokenKind.AdminAccount  => (t, UserRole.Admin)
      }
      .liftTo[Future](AccountActivationTokenNotFoundOrInvalid(token))
    _ = logger.debug(s"Token $token found, creating user with role $userRole")
    _ <- userOrchestrator.createUser(draftUser, accessToken, userRole)
    _ = logger.debug(s"User created successfully, invalidating token")
    _ <- accessTokenRepository.invalidateToken(accessToken)
    _ = logger.debug(s"Token has been revoked")
  } yield ()

  def fetchDGCCRFUserActivationToken(token: String): Future[DGCCRFUserActivationToken] = for {
    maybeAccessToken <- accessTokenRepository
      .findToken(token)
    accessToken <- maybeAccessToken
      .map(Future.successful(_))
      .getOrElse(Future.failed[AccessToken](DGCCRFActivationTokenNotFound(token)))
    _ = logger.debug("Validating email")
    emailTo <-
      accessToken.emailedTo
        .map(Future.successful(_))
        .getOrElse(Future.failed[EmailAddress](ServerError(s"Email should be defined for access token $token")))
    _ = logger.debug(s"Access token found ${accessToken}")
  } yield accessToken
    .into[DGCCRFUserActivationToken]
    .withFieldConst(_.emailedTo, emailTo)
    .transform

  def sendDGCCRFInvitation(email: EmailAddress): Future[Unit] =
    sendAdminOrDgccrfInvitation(email, TokenKind.DGCCRFAccount)

  def sendAdminInvitation(email: EmailAddress): Future[Unit] =
    sendAdminOrDgccrfInvitation(email, TokenKind.AdminAccount)

  def sendAdminOrDgccrfInvitation(email: EmailAddress, kind: AdminOrDgccrfTokenKind): Future[Unit] = {
    val (emailRegexp, joinDuration, emailTemplate, invitationUrlFunction) = kind match {
      // TODO ajuster tests
      // TODO faire TU sur les regexp
      case DGCCRFAccount =>
        (
          emailConfiguration.dgccrfEmailRegexp,
          tokenConfiguration.dgccrfJoinDuration,
          DgccrfAccessLink,
          frontRoute.dashboard.Dgccrf.register _
        )
      case AdminAccount =>
        (
          emailConfiguration.adminEmailRegexp,
          tokenConfiguration.adminJoinDuration,
          AdminAccessLink,
          frontRoute.dashboard.Admin.register _
        )
    }
    for {
      _ <-
        if (emailRegexp.r.matches(email.value)) {
          Future.successful(())
        } else {
          Future.failed(InvalidDGCCRFOrAdminEmail(email, emailRegexp))
        }
      _ <- userOrchestrator.find(email).ensure(UserAccountEmailAlreadyExist)(_.isEmpty)
      existingTokens <- accessTokenRepository.fetchPendingTokens(email)
      existingToken = existingTokens.find(_.emailedTo.contains(email))
      token <-
        existingToken match {
          case Some(token) =>
            logger.debug("reseting token validity")
            accessTokenRepository.update(
              token.id,
              AccessToken.resetExpirationDate(token, joinDuration)
            )
          case None =>
            logger.debug("creating token")
            accessTokenRepository.create(
              AccessToken.build(
                kind = kind,
                token = UUID.randomUUID.toString,
                validity = joinDuration,
                companyId = None,
                level = None,
                emailedTo = Some(email)
              )
            )
        }
      _ <- mailService.send(
        emailTemplate(email, invitationUrlFunction(token.token))
      )
      _ = logger.debug(s"Sent DGCCRF account invitation to ${email}")
    } yield ()
  }

  def sendEmailValidation(user: User): Future[Unit] =
    for {
      token <- accessTokenRepository.create(
        AccessToken.build(
          kind = ValidateEmail,
          token = UUID.randomUUID.toString,
          validity = tokenConfiguration.dgccrfRevalidationTokenDuration,
          companyId = None,
          level = None,
          emailedTo = Some(user.email)
        )
      )
      _ <- mailService.send(
        Email.ValidateEmail(
          user,
          tokenConfiguration.dgccrfRevalidationTokenDuration.map(_.getDays).getOrElse(7),
          frontRoute.dashboard.validateEmail(token.token)
        )
      )
    } yield logger.debug(s"Sent email validation to ${user.email}")

  def validateDGCCRFEmail(token: String): Future[User] =
    for {
      accessToken <- accessTokenRepository.findToken(token)
      emailValidationToken <- accessToken
        .filter(_.kind == ValidateEmail)
        .liftTo[Future](DGCCRFActivationTokenNotFound(token))

      emailTo <- emailValidationToken.emailedTo.liftTo[Future](
        ServerError("ValidateEmailToken should have valid email associated")
      )
      user <- userOrchestrator.findOrError(emailTo)
      _ <- accessTokenRepository.validateEmail(emailValidationToken, user)
      _ = logger.debug(s"Validated email ${emailValidationToken.emailedTo.get}")
      updatedUser <- userOrchestrator.findOrError(emailTo)
    } yield updatedUser

  def resetLastEmailValidation(email: EmailAddress): Future[User] = for {
    tokens <- accessTokenRepository.fetchPendingTokens(email)
    _ = logger.debug("Fetching email validation token")
    emailValidationToken = tokens.filter(_.kind == ValidateEmail)
    _ = logger.debug(s"Found email validation token : $emailValidationToken")
    _ = logger.debug(s"Fetching user for $email")
    user <- userOrchestrator
      .findOrError(email)
      .ensure {
        logger.error("Cannot revalidate user with role different from DGCCRF")
        CantPerformAction
      }(_.userRole == UserRole.DGCCRF)
    _ = logger.debug(s"Validating DGCCRF user email")
    _ <-
      if (emailValidationToken.nonEmpty) {
        emailValidationToken.map(accessTokenRepository.validateEmail(_, user)).sequence
      } else accessTokenRepository.updateLastEmailValidation(user)
    _ = logger.debug(s"Successfully validated email ${email}")
    updatedUser <- userOrchestrator.findOrError(user.email)
  } yield updatedUser
}
