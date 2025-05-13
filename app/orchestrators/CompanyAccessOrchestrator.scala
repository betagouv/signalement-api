package orchestrators

import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxOption
import cats.implicits.catsSyntaxOptionId
import controllers.error.AppError._
import models.AccessToken
import models.User
import models.UserRole.Admin
import models.UserRole.DGAL
import models.UserRole.DGCCRF
import models.UserRole.Professionnel
import models.UserRole.ReadOnlyAdmin
import models.UserRole.SuperAdmin
import models.access.UserWithAccessLevel.toApi
import models.access.ActivationLinkRequest
import models.access.UserWithAccessLevel
import models.access.UserWithAccessLevelAndNbResponse
import models.company.AccessLevel
import models.company.Company
import models.company.CompanyActivationAttempt
import play.api.Logger
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.companyactivationattempt.CompanyActivationAttemptRepositoryInterface
import repositories.event.EventFilter
import repositories.event.EventRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.EventsBuilder.userAccessRemovedEvent
import utils.Constants.ActionEvent.REPORT_PRO_RESPONSE
import utils.Logs.RichLogger
import utils.SIREN
import utils.SIRET

import java.time.OffsetDateTime.now
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._

class CompanyAccessOrchestrator(
    companyAccessRepository: CompanyAccessRepositoryInterface,
    val companyRepository: CompanyRepositoryInterface,
    val accessTokenRepository: AccessTokenRepositoryInterface,
    val companyActivationAttemptRepository: CompanyActivationAttemptRepositoryInterface,
    val eventsRepository: EventRepositoryInterface,
    val proAccessTokenOrchestrator: ProAccessTokenOrchestrator,
    val userOrchestrator: UserOrchestrator,
    val userRepository: UserRepositoryInterface
)(implicit val ec: ExecutionContext) {

  val logger = Logger(this.getClass)

  def sendActivationLink(siret: SIRET, activationLinkRequest: ActivationLinkRequest): Future[Unit] = {
    val future = for {
      _ <- checkActivationAttempts(siret)
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
          .findActivationToken(company.id, activationLinkRequest.token)
          .flatMap(_.liftTo[Future] {
            logger.warn(s"Activation token not found for siret $siret, given code is not valid")
            CompanyActivationSiretOrCodeInvalid(siret)
          })
      _ = logger.debug("Token found")
      _ <- validateToken(token, siret)
      _ = logger.debug("Token validated")
      _ <- proAccessTokenOrchestrator.addUserOrInvite(company, activationLinkRequest.email, AccessLevel.ADMIN, None)
    } yield ()

    future.recoverWith {
      case error: TooMuchCompanyActivationAttempts => Future.failed(error)
      case error =>
        val attempt = CompanyActivationAttempt.build(siret)
        companyActivationAttemptRepository
          .create(attempt)
          .flatMap(_ => Future.failed(error))
    }
  }
  private def checkActivationAttempts(siret: SIRET): Future[Unit] =
    for {
      num <- companyActivationAttemptRepository
        .countAttempts(siret.value, 30.minutes)
      _ = logger.debug(s"Found ${num} activation attempts")
      result <-
        if (num >= 20) Future.failed(TooMuchCompanyActivationAttempts(siret))
        else Future.unit
    } yield result

  private def validateToken(
      accessToken: AccessToken,
      siret: SIRET
  ): Future[Unit] =
    if (!accessToken.valid) {
      logger.warn(s"Unable to activate company $siret, code has already been used.")
      Future.failed(ActivationCodeAlreadyUsed())
    } else if (accessToken.expirationDate.exists(expiration => now isAfter expiration)) {
      logger.warn(s"Unable to activate company $siret, code has expired.")
      Future.failed(CompanyActivationCodeExpired(siret))
    } else Future.unit

  def listAccesses(company: Company, requestedBy: User): Future[List[UserWithAccessLevel]] =
    for {
      userLevel <- companyAccessRepository.getUserLevel(company.id, requestedBy)
      accesses  <- getUserAccess(requestedBy, userLevel, List(company), editable = true)
    } yield accesses

  def listAccessesMostActive(company: Company, user: User): Future[List[UserWithAccessLevelAndNbResponse]] =
    for {
      accesses <- listAccesses(company, user)
      nbResponsesByUserIds <- eventsRepository.countCompanyEventsByUsers(
        companyId = company.id,
        usersIds = accesses.map(_.userId),
        EventFilter(action = Some(REPORT_PRO_RESPONSE))
      )
      accessesWithNbResponses = accesses.map(access =>
        UserWithAccessLevelAndNbResponse.build(access, nbResponsesByUserIds.getOrElse(access.userId, 0))
      )
      mostActive = accessesWithNbResponses.sortBy(-_.nbResponses).take(3)
    } yield mostActive

  private def getHeadOffice(company: Company): Future[Option[Company]] =
    companyRepository
      .findHeadOffices(List(SIREN.fromSIRET(company.siret)), openOnly = false)
      .flatMap {
        case Nil =>
          logger.warn(s"No head office for siret ${company.siret}")
          Future.successful(None)
        case c :: Nil =>
          Future.successful(Some(c))
        case companies =>
          logger.errorWithTitle(
            "multiple_head_offices",
            s"Multiple head offices for siret ${company.siret} company data ids ${companies.map(_.id)} "
          )
          // multiple_head_offices error should be investigated, but for now we are considering that last created company is the head office.
          companies.maxBy(_.creationDate.toEpochSecond).some.pure[Future]
      }

  private def getUserAccess(
      user: User,
      userLevel: AccessLevel,
      companies: List[Company],
      editable: Boolean
  ): Future[List[UserWithAccessLevel]] =
    for {
      companyAccess <- companyAccessRepository
        .fetchUsersWithLevelExcludingNone(companies.map(_.id))
    } yield (userLevel, user.userRole) match {
      case (_, SuperAdmin) | (_, Admin) | (_, ReadOnlyAdmin) =>
        logger.debug(s"Signal conso admin user : setting editable to true")
        companyAccess.map { case (user, level) => toApi(user, level, editable = true) }
      case (_, DGCCRF) =>
        logger.debug(s"Signal conso dgccrf user : setting editable to false")
        companyAccess.map { case (user, level) => toApi(user, level, editable = false) }
      case (AccessLevel.ADMIN, Professionnel) =>
        companyAccess.map {
          case (companyUser, level) if companyUser.id == user.id =>
            toApi(companyUser, level, editable = false)
          case (companyUser, level) =>
            toApi(companyUser, level, editable)
        }
      case (_, Professionnel) =>
        logger.debug(s"User PRO does not have admin access to company : setting editable to false")
        companyAccess.map { case (user, level) => toApi(user, level, editable = false) }
      case (_, DGAL) =>
        logger.error(s"User is not supposed to access this feature")
        List.empty[UserWithAccessLevel]
    }

  def removeAccess(companyId: UUID, user: User, requestedBy: User): Future[Unit] =
    for {
      _ <- companyAccessRepository.createAccess(companyId, user.id, AccessLevel.NONE)
      _ <- eventsRepository
        .create(userAccessRemovedEvent(companyId, user, requestedBy))
        .map(_ => ())
    } yield ()

  def removeAccessesIfExist(companiesIds: List[UUID], users: List[User], requestedBy: User) =
    for {
      existingAccesses <- companyAccessRepository.getUserAccesses(companiesIds, users.map(_.id))
      _ <- Future.sequence(
        existingAccesses.map { access =>
          val userId = access.userId
          val user = users
            .find(_.id == userId)
            .getOrElse(throw ServerError(s"Can't remove access of $userId, it was not in the original list of users"))
          removeAccess(companyId = access.companyId, user = user, requestedBy = requestedBy)
        }
      )
    } yield ()

  def duplicateAccessesFromHeadOffice(company: Company): Future[Unit] =
    if (company.isHeadOffice) { Future.unit }
    else {
      for {
        maybeHeadOffice <- getHeadOffice(company)
        _ <- maybeHeadOffice match {
          case Some(headOffice) =>
            for {
              headOfficesAccesses <- companyAccessRepository.fetchUsersWithLevelExcludingNone(List(headOffice.id))
              _ <- Future.sequence(headOfficesAccesses.map { case (user, level) =>
                companyAccessRepository.createAccess(company.id, user.id, level)
              })
            } yield ()
          case None => Future.unit
        }
      } yield ()
    }

}
