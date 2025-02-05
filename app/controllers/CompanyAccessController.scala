package controllers

import authentication.Authenticator
import authentication.actions.ImpersonationAction.ForbidImpersonation
import authentication.actions.UserAction.WithRole
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError.UserNotFoundById
import models.User
import models.UserRole
import models.access.ActivationLinkRequest
import models.company.AccessLevel
import models.event.Event
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyAccessOrchestrator
import orchestrators.ProAccessTokenOrchestrator
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.user.UserRepositoryInterface
import utils.Constants
import utils.EmailAddress
import utils.SIRET

import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyAccessController(
    userRepository: UserRepositoryInterface,
    val companyRepository: CompanyRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    accessesOrchestrator: ProAccessTokenOrchestrator,
    val companyVisibilityOrch: CompaniesVisibilityOrchestrator,
    companyAccessOrchestrator: CompanyAccessOrchestrator,
    eventRepository: EventRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def listAccesses(siret: String) = withCompanyAccess(siret).async { implicit request =>
    companyAccessOrchestrator
      .listAccesses(request.company, request.identity)
      .map(userWithAccessLevel => Ok(Json.toJson(userWithAccessLevel)))
  }

  def listAccessesMostActive(siret: String) = withCompanyAccess(siret).async { implicit request =>
    companyAccessOrchestrator
      .listAccessesMostActive(request.company, request.identity)
      .map(mostActive => Ok(Json.toJson(mostActive)))
  }

  def countAccesses(siret: String) = withCompanyAccess(siret).async { implicit request =>
    companyAccessOrchestrator
      .listAccesses(request.company, request.identity)
      .map(_.length)
      .map(count => Ok(Json.toJson(count)))
  }

  // Is this used ??
  def myCompanies = SecuredAction.async { implicit request =>
    companyAccessRepository
      .fetchCompaniesWithLevel(request.identity)
      .map(companies => Ok(Json.toJson(companies)))
  }

  def visibleUsersToPro = SecuredAction.andThen(WithRole(UserRole.Professionnel)).async { implicit request =>
    for {
      companiesWithAccesses <- companyVisibilityOrch.fetchVisibleCompanies(request.identity)
      onlyAdminCompanies = companiesWithAccesses.filter(_.level == AccessLevel.ADMIN)
      usersAccessesPerCompanyMap <- companyAccessRepository.fetchUsersByCompanyIds(onlyAdminCompanies.map(_.company.id))
    } yield {
      val companiesPerUser =
        usersAccessesPerCompanyMap.toList.flatMap { case (uuid, users) => users.map(_ -> uuid) }.groupMap(_._1)(_._2)
      val companyCountPerUser =
        companiesPerUser.view.mapValues(_.length).toList.map(tuple => Json.obj("user" -> tuple._1, "count" -> tuple._2))
      Ok(Json.toJson(companyCountPerUser))
    }
  }

  def inviteProToMyCompanies(email: String) =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).andThen(ForbidImpersonation).async { implicit request =>
      for {
        accesses  <- companyAccessRepository.fetchCompaniesWithLevel(request.identity)
        maybeUser <- userRepository.findByEmail(email)
        _ <- maybeUser match {
          case Some(user) =>
            accessesOrchestrator.addInvitedUserAndNotify(user, accesses.map(_.company), AccessLevel.ADMIN)
          case None =>
            accessesOrchestrator.sendInvitations(accesses.map(_.company), EmailAddress(email), AccessLevel.ADMIN)
        }
      } yield Ok(email)

    }

  def revokeProFromMyCompanies(userId: UUID) =
    SecuredAction.andThen(WithRole(UserRole.Professionnel)).andThen(ForbidImpersonation).async { implicit request =>
      for {
        maybeUser             <- userRepository.get(userId)
        user                  <- maybeUser.liftTo[Future](UserNotFoundById(userId))
        companiesWithAccesses <- companyVisibilityOrch.fetchVisibleCompanies(request.identity)
        onlyAdminCompanies = companiesWithAccesses.filter(_.level == AccessLevel.ADMIN)
        usersAccesses <- companyAccessRepository.getUserAccesses(onlyAdminCompanies.map(_.company.id), userId)
        _             <- usersAccesses.traverse(c => removeAccessFor(c.companyId, user, request.identity))
      } yield Ok(user.email.value)
    }

  private def removeAccessFor(companyId: UUID, user: User, requestBy: User) =
    for {
      _ <- companyAccessRepository.createUserAccess(companyId, user.id, AccessLevel.NONE)
      _ <- eventRepository.create(
        Event(
          UUID.randomUUID(),
          None,
          Some(companyId),
          Some(requestBy.id),
          OffsetDateTime.now(),
          Constants.EventType.fromUserRole(requestBy.userRole),
          Constants.ActionEvent.USER_ACCESS_REMOVED,
          Json.obj("userId" -> user.id, "email" -> user.email)
        )
      )
    } yield ()

  def updateAccess(siret: String, userId: UUID) = withCompanyAccess(siret, adminLevelOnly = true).async {
    implicit request =>
      request.body.asJson
        .map(json => (json \ "level").as[AccessLevel])
        .map(level =>
          for {
            user <- userRepository.get(userId)
            _ <- user
              .map(u => companyAccessRepository.createUserAccess(request.company.id, u.id, level))
              .getOrElse(Future.unit)
          } yield if (user.isDefined) Ok else NotFound
        )
        .getOrElse(Future.successful(NotFound))
  }

  def removeAccess(siret: String, userId: UUID) = withCompanyAccess(siret, adminLevelOnly = true).async {
    implicit request =>
      for {
        maybeUser <- userRepository.get(userId)
        user      <- maybeUser.liftTo[Future](UserNotFoundById(userId))
        _         <- companyAccessRepository.createUserAccess(request.company.id, user.id, AccessLevel.NONE)
        _ <- eventRepository.create(
          Event(
            UUID.randomUUID(),
            None,
            Some(request.company.id),
            Some(request.identity.id),
            OffsetDateTime.now(),
            Constants.EventType.fromUserRole(request.identity.userRole),
            Constants.ActionEvent.USER_ACCESS_REMOVED,
            Json.obj("userId" -> userId, "email" -> user.email)
          )
        )
        // this operation may leave some reports assigned to this user, to which he doesn't have access anymore
        // in theory here we should find these reports and de-assign them
      } yield NoContent
  }

  case class AccessInvitation(email: EmailAddress, level: AccessLevel)

  def sendInvitation(siret: String) = withCompanyAccess(siret, adminLevelOnly = true).async(parse.json) {
    implicit request =>
      implicit val reads = Json.reads[AccessInvitation]
      request.body
        .validate[AccessInvitation]
        .fold(
          errors => Future.successful(BadRequest(JsError.toJson(errors))),
          invitation =>
            accessesOrchestrator
              .addUserOrInvite(request.company, invitation.email, invitation.level, Some(request.identity))
              .map(_ => Ok)
        )
  }

  def listPendingTokens(siret: String) = withCompanyAccess(siret, adminLevelOnly = true).async { implicit request =>
    accessesOrchestrator
      .listProPendingToken(request.company, request.identity)
      .map(tokens => Ok(Json.toJson(tokens)))
  }

  def removePendingToken(siret: String, tokenId: UUID) = withCompanyAccess(siret, adminLevelOnly = true).async {
    implicit request =>
      for {
        token <- accessTokenRepository.getToken(request.company, tokenId)
        _     <- token.map(accessTokenRepository.invalidateToken).getOrElse(Future.unit)
      } yield if (token.isDefined) Ok else NotFound
  }

  def fetchTokenInfo(siret: String, token: String) = IpRateLimitedAction2.async { _ =>
    accessesOrchestrator
      .fetchCompanyUserActivationToken(SIRET.fromUnsafe(siret), token)
      .map(token => Ok(Json.toJson(token)))
  }

  def sendActivationLink(siret: String) = IpRateLimitedAction2.async(parse.json) { implicit request =>
    for {
      activationLinkRequest <- request.parseBody[ActivationLinkRequest]()
      _ <- companyAccessOrchestrator.sendActivationLink(SIRET.fromUnsafe(siret), activationLinkRequest)
    } yield Ok

  }

  case class AcceptTokenRequest(token: String)

  def acceptToken(siret: String) = SecuredAction.async(parse.json) { implicit request =>
    implicit val reads = Json.reads[AcceptTokenRequest]
    request.body
      .validate[AcceptTokenRequest]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        acceptTokenRequest =>
          for {
            company <- companyRepository.findBySiret(SIRET.fromUnsafe(siret))
            token <- company
              .map(
                accessTokenRepository
                  .findValidToken(_, acceptTokenRequest.token)
                  .map(
                    _.filter(
                      !_.emailedTo.exists(_ != request.identity.email)
                    )
                  )
              )
              .getOrElse(Future.successful(None))
            applied <- token
              .map(t =>
                accessTokenRepository
                  .createCompanyAccessAndRevokeToken(t, request.identity)
              )
              .getOrElse(Future.successful(false))
          } yield if (applied) Ok else NotFound
      )
  }

  def proFirstActivationCount(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    accessesOrchestrator.proFirstActivationCount(ticks).map(x => Ok(Json.toJson(x)))
  }

}
