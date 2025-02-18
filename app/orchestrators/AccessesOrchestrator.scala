package orchestrators

import cats.implicits.catsSyntaxMonadError
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import config.TokenConfiguration
import controllers.error.AppError._
import io.scalaland.chimney.dsl._
import models.AuthProvider.ProConnect
import models._
import models.token.AdminOrDgccrfTokenKind
import models.token.AgentAccessToken
import models.token.DGCCRFUserActivationToken
import models.token.TokenKind
import models.token.TokenKind.AdminAccount
import models.token.TokenKind.DGALAccount
import models.token.TokenKind.DGCCRFAccount
import models.token.TokenKind.ReadOnlyAdminAccount
import models.token.TokenKind.SuperAdminAccount
import models.token.TokenKind.UpdateEmail
import models.token.TokenKind.ValidateEmail
import play.api.Logger
import repositories.accesstoken.AccessTokenRepositoryInterface
import services.EmailAddressService
import services.emails.EmailDefinitionsAdmin.AdminAccessLink
import services.emails.EmailDefinitionsDggcrf.DgccrfAgentAccessLink
import services.emails.EmailDefinitionsDggcrf.DgccrfAgentInvitation
import services.emails.EmailDefinitionsDggcrf.DgccrfValidateEmail
import services.emails.EmailDefinitionsVarious.UpdateEmailAddress
import services.emails.MailServiceInterface
import utils.EmailAddress
import utils.FrontRoute
import utils.PasswordComplexityHelper

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.jdk.DurationConverters._

class AccessesOrchestrator(
    userOrchestrator: UserOrchestratorInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    mailService: MailServiceInterface,
    frontRoute: FrontRoute,
    tokenConfiguration: TokenConfiguration
)(implicit val executionContext: ExecutionContext) {

  val logger                                          = Logger(this.getClass)
  implicit val timeout: org.apache.pekko.util.Timeout = 5.seconds

  private def tokenKindToUserRole(tokenKind: TokenKind) = tokenKind match {
    case TokenKind.DGALAccount   => Some(UserRole.DGAL)
    case TokenKind.DGCCRFAccount => Some(UserRole.DGCCRF)
    case _                       => None
  }

  def updateEmailAddress(user: User, token: String): Future[User] =
    for {
      accessToken      <- accessTokenRepository.findToken(token)
      updateEmailToken <- accessToken.filter(_.kind == UpdateEmail).liftTo[Future](UpdateEmailTokenNotFound(token))
      isSameUser = updateEmailToken.userId.contains(user.id)
      emailedTo <- updateEmailToken.emailedTo.liftTo[Future](
        ServerError(s"Email should be defined for access token $token")
      )
      _ <- userOrchestrator.find(emailedTo).ensure(EmailAlreadyExist)(user => user.isEmpty)
      _ <- user.userRole match {
        case UserRole.DGAL | UserRole.DGCCRF =>
          accessTokenRepository.validateEmail(updateEmailToken, user)
        case UserRole.SuperAdmin | UserRole.Admin | UserRole.ReadOnlyAdmin | UserRole.Professionnel =>
          accessTokenRepository.invalidateToken(updateEmailToken)
      }
      updatedUser <-
        if (isSameUser) userOrchestrator.updateEmail(user, emailedTo)
        else Future.failed(DifferentUserFromRequest(user.id, updateEmailToken.userId))
    } yield updatedUser

  def sendEmailAddressUpdateValidation(user: User, newEmail: EmailAddress): Future[Unit] = {
    val emailValidationFunction = user.userRole match {
      case UserRole.SuperAdmin | UserRole.Admin | UserRole.ReadOnlyAdmin =>
        EmailAddressService.isEmailAcceptableForAdminAccount _
      case UserRole.DGCCRF =>
        EmailAddressService.isEmailAcceptableForDgccrfAccount _
      case UserRole.DGAL =>
        EmailAddressService.isEmailAcceptableForDgalAccount _
      case UserRole.Professionnel => (_: String) => true
    }

    for {
      _ <- userOrchestrator.find(newEmail).ensure(EmailAlreadyExist)(user => user.isEmpty)
      _ <-
        if (emailValidationFunction(newEmail.value)) Future.unit
        else Future.failed(InvalidDGCCRFOrAdminEmail(List(newEmail)))
      existingTokens <- accessTokenRepository.fetchPendingTokens(user)
      existingToken = existingTokens.headOption
      token <-
        existingToken match {
          case Some(token) if token.emailedTo.contains(newEmail) =>
            logger.debug("reseting token validity and email")
            accessTokenRepository.update(
              token.id,
              AccessToken.resetExpirationDate(token, tokenConfiguration.updateEmailAddressDuration)
            )
          case Some(token) =>
            logger.debug("invalidating old token and create new one")
            for {
              _ <- accessTokenRepository.invalidateToken(token)
              createdToken <- accessTokenRepository.create(
                AccessToken.build(
                  kind = UpdateEmail,
                  token = UUID.randomUUID.toString,
                  validity = Some(tokenConfiguration.updateEmailAddressDuration),
                  companyId = None,
                  level = None,
                  emailedTo = Some(newEmail),
                  userId = Some(user.id)
                )
              )
            } yield createdToken
          case None =>
            logger.debug("creating token")
            accessTokenRepository.create(
              AccessToken.build(
                kind = UpdateEmail,
                token = UUID.randomUUID.toString,
                validity = Some(tokenConfiguration.updateEmailAddressDuration),
                companyId = None,
                level = None,
                emailedTo = Some(newEmail),
                userId = Some(user.id)
              )
            )
        }
      _ <- mailService.send(
        UpdateEmailAddress.Email(
          newEmail,
          frontRoute.dashboard.updateEmail(token.token),
          tokenConfiguration.updateEmailAddressDuration.getDays
        )
      )
    } yield ()
  }

  def listAgentPendingTokens(maybeRequestedRole: Option[UserRole]): Future[List[AgentAccessToken]] =
    accessTokenRepository.fetchPendingAgentTokens
      .map(
        _.flatMap { token =>
          val maybeUserRole = tokenKindToUserRole(token.kind)
          (maybeUserRole, maybeRequestedRole) match {
            case (Some(userRole), Some(requestedRole)) if userRole == requestedRole =>
              Some(
                AgentAccessToken(token.creationDate, token.token, token.emailedTo, token.expirationDate, userRole)
              )
            case (Some(userRole), None) =>
              Some(
                AgentAccessToken(token.creationDate, token.token, token.emailedTo, token.expirationDate, userRole)
              )
            case _ => None
          }
        }
      )

  def activateAdminOrAgentUser(draftUser: DraftUser, token: String): Future[User] = for {
    _                <- PasswordComplexityHelper.validatePasswordComplexity(draftUser.password)
    maybeAccessToken <- accessTokenRepository.findToken(token)
    (accessToken, userRole) <- maybeAccessToken
      .collect {
        case t if t.kind == TokenKind.DGCCRFAccount        => (t, UserRole.DGCCRF)
        case t if t.kind == TokenKind.DGALAccount          => (t, UserRole.DGAL)
        case t if t.kind == TokenKind.SuperAdminAccount    => (t, UserRole.SuperAdmin)
        case t if t.kind == TokenKind.AdminAccount         => (t, UserRole.Admin)
        case t if t.kind == TokenKind.ReadOnlyAdminAccount => (t, UserRole.ReadOnlyAdmin)
      }
      .liftTo[Future](AccountActivationTokenNotFoundOrInvalid(token))
    _ = logger.debug(s"Token $token found, creating user with role $userRole")
    user <- userOrchestrator.createSignalConsoUser(draftUser, accessToken, userRole)
    _ = logger.debug(s"User created successfully, invalidating token")
    _ <- accessTokenRepository.invalidateToken(accessToken)
    _ = logger.debug(s"Token has been revoked")
  } yield user

  def fetchDGCCRFUserActivationToken(token: String): Future[DGCCRFUserActivationToken] = for {
    maybeAccessToken <- accessTokenRepository
      .findToken(token)
    accessToken <- maybeAccessToken
      .map(Future.successful(_))
      .getOrElse(Future.failed[AccessToken](AgentActivationTokenNotFound(token)))
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

  private def parseEmails(emails: List[String]): List[EmailAddress] =
    emails
      .flatMap(email => email.split(",").toList)
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(email => EmailAddress(email))

  private def validateEmails(emails: List[EmailAddress], validateFunction: String => Boolean) =
    if (emails.isEmpty)
      Future.failed(InvalidDGCCRFOrAdminEmail(List.empty))
    else {
      val invalidEmails = emails.filter(email => !validateFunction(email.value))
      if (invalidEmails.isEmpty) {
        Future.unit
      } else {
        Future.failed(InvalidDGCCRFOrAdminEmail(invalidEmails))
      }
    }

  def sendAgentsInvitations(role: UserRole, emails: List[String]): Future[List[Unit]] =
    role match {
      case UserRole.DGCCRF =>
        val parsedEmails = parseEmails(emails)
        for {
          _   <- validateEmails(parsedEmails, EmailAddressService.isEmailAcceptableForDgccrfAccount)
          res <- Future.sequence(parsedEmails.map(email => sendAdminOrAgentInvitation(email, TokenKind.DGCCRFAccount)))
        } yield res
      case UserRole.DGAL =>
        val parsedEmails = parseEmails(emails)
        for {
          _   <- validateEmails(parsedEmails, EmailAddressService.isEmailAcceptableForDgalAccount)
          res <- Future.sequence(parsedEmails.map(sendDGALInvitation))
        } yield res
      case _ => Future.failed(WrongUserRole(role))
    }

  def sendDGCCRFInvitation(invitationRequest: InvitationRequest): Future[Unit] =
    invitationRequest.authProvider match {
      case Some(ProConnect) =>
        for {
          _ <- userOrchestrator.createProConnectUser(invitationRequest.email, UserRole.DGCCRF)
          _ <- mailService.send(
            DgccrfAgentInvitation.Email("DGCCRF")(invitationRequest.email, frontRoute.dashboard.loginProConnect)
          )
        } yield ()
      case _ => sendAdminOrAgentInvitation(invitationRequest.email, TokenKind.DGCCRFAccount)
    }

  def sendDGALInvitation(email: EmailAddress): Future[Unit] =
    sendAdminOrAgentInvitation(email, TokenKind.DGALAccount)

  def sendSuperAdminInvitation(email: EmailAddress): Future[Unit] =
    sendAdminOrAgentInvitation(email, TokenKind.SuperAdminAccount)

  def sendAdminInvitation(email: EmailAddress): Future[Unit] =
    sendAdminOrAgentInvitation(email, TokenKind.AdminAccount)

  def sendReadOnlyAdminInvitation(email: EmailAddress): Future[Unit] =
    sendAdminOrAgentInvitation(email, TokenKind.ReadOnlyAdminAccount)

  private def sendAdminOrAgentInvitation(email: EmailAddress, kind: AdminOrDgccrfTokenKind): Future[Unit] = {
    val (emailValidationFunction, joinDuration, emailTemplate, invitationUrlFunction) = kind match {
      case DGCCRFAccount =>
        (
          EmailAddressService.isEmailAcceptableForDgccrfAccount _,
          tokenConfiguration.dgccrfJoinDuration,
          DgccrfAgentAccessLink.Email("DGCCRF") _,
          frontRoute.dashboard.Agent.register _
        )
      case DGALAccount =>
        (
          EmailAddressService.isEmailAcceptableForDgalAccount _,
          tokenConfiguration.dgccrfJoinDuration,
          DgccrfAgentAccessLink.Email("DGAL") _,
          frontRoute.dashboard.Agent.register _
        )
      case AdminAccount | SuperAdminAccount | ReadOnlyAdminAccount =>
        (
          EmailAddressService.isEmailAcceptableForAdminAccount _,
          tokenConfiguration.adminJoinDuration.toJava,
          AdminAccessLink.Email,
          frontRoute.dashboard.Admin.register _
        )
    }
    for {
      _ <-
        if (emailValidationFunction(email.value)) {
          Future.unit
        } else {
          Future.failed(InvalidDGCCRFOrAdminEmail(List(email)))
        }
      _              <- userOrchestrator.find(email).ensure(UserAccountEmailAlreadyExist)(_.isEmpty)
      existingTokens <- accessTokenRepository.fetchPendingTokens(email)
      existingToken = kind match {
        // Kinds are grouped to avoid having multiple tokens for one user
        case SuperAdminAccount | AdminAccount | ReadOnlyAdminAccount =>
          existingTokens.find(t =>
            t.kind == SuperAdminAccount || t.kind == AdminAccount || t.kind == ReadOnlyAdminAccount
          )
        case DGALAccount | DGCCRFAccount => existingTokens.find(t => t.kind == DGCCRFAccount || t.kind == DGALAccount)
      }
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
                validity = Some(joinDuration),
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
        DgccrfValidateEmail.Email(
          user.email,
          tokenConfiguration.dgccrfRevalidationTokenDuration.map(_.getDays).getOrElse(7),
          frontRoute.dashboard.validateEmail(token.token)
        )
      )
    } yield logger.debug(s"Sent email validation to ${user.email}")

  def validateAgentEmail(token: String): Future[User] =
    for {
      accessToken <- accessTokenRepository.findToken(token)
      emailValidationToken <- accessToken
        .filter(_.kind == ValidateEmail)
        .liftTo[Future](AgentActivationTokenNotFound(token))

      emailTo <- emailValidationToken.emailedTo.liftTo[Future](
        ServerError("ValidateEmailToken should have valid email associated")
      )
      user <- userOrchestrator.findOrError(emailTo)
      _    <- accessTokenRepository.validateEmail(emailValidationToken, user)
      _ = logger.debug(s"Validated email ${emailValidationToken.emailedTo.get}")
      updatedUser <- userOrchestrator.findOrError(emailTo)
    } yield updatedUser

  def resetLastEmailValidation(email: EmailAddress): Future[User] = for {
    tokens <- accessTokenRepository.fetchPendingTokens(email)
    _                    = logger.debug("Fetching email validation token")
    emailValidationToken = tokens.filter(_.kind == ValidateEmail)
    _                    = logger.debug(s"Found email validation token : $emailValidationToken")
    _                    = logger.debug(s"Fetching user for $email")
    user <- userOrchestrator
      .findOrError(email)
      .ensure {
        logger.error("Cannot revalidate user with role different from DGCCRF or DGAL")
        CantPerformAction
      }(user => user.userRole == UserRole.DGCCRF || user.userRole == UserRole.DGAL)
    _ = logger.debug(s"Validating agent user email")
    _ <-
      if (emailValidationToken.nonEmpty) {
        emailValidationToken.map(accessTokenRepository.validateEmail(_, user)).sequence
      } else accessTokenRepository.updateLastEmailValidation(user)
    _ = logger.debug(s"Successfully validated email ${email}")
    updatedUser <- userOrchestrator.findOrError(user.email)
  } yield updatedUser
}
