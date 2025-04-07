package controllers

import authentication.Authenticator
import cats.implicits.catsSyntaxOption
import cats.implicits.toTraverseOps
import controllers.error.AppError
import controllers.error.AppError.UserNotFoundById
import models.User
import models.access.ActivationLinkRequest
import models.company.AccessLevel
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyAccessOrchestrator
import orchestrators.CompanyOrchestrator
import orchestrators.ProAccessTokenOrchestrator
import play.api.Logger
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.company.CompanyRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.user.UserRepositoryInterface
import services.EmailAddressService.isAgentEmail
import utils.Constants
import utils.EmailAddress
import utils.SIRET

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class CompanyAccessController(
    userRepository: UserRepositoryInterface,
    companyRepository: CompanyRepositoryInterface,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    val companyOrchestrator: CompanyOrchestrator,
    accessesOrchestrator: ProAccessTokenOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    companyAccessOrchestrator: CompanyAccessOrchestrator,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseCompanyController(authenticator, controllerComponents) {

  val logger: Logger = Logger(this.getClass)

  def listAccesses(siret: String) =
    Act.securedWithCompanyAccessBySiret(siret).allowImpersonation.async { implicit request =>
      companyAccessOrchestrator
        .listAccesses(request.company, request.identity)
        .map(userWithAccessLevel => Ok(Json.toJson(userWithAccessLevel)))
    }

  def listAccessesMostActive(siret: String) =
    Act.securedWithCompanyAccessBySiret(siret).allowImpersonation.async { implicit request =>
      companyAccessOrchestrator
        .listAccessesMostActive(request.company, request.identity)
        .map(mostActive => Ok(Json.toJson(mostActive)))
    }

  def countAccesses(siret: String) =
    Act.securedWithCompanyAccessBySiret(siret).allowImpersonation.async { implicit request =>
      companyAccessOrchestrator
        .listAccesses(request.company, request.identity)
        .map(_.length)
        .map(count => Ok(Json.toJson(count)))
    }

  def visibleUsersToPro = Act.secured.disabled.async { implicit request =>
    for {
      companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(request.identity)
      onlyAdminCompanies = companiesWithAccesses.filter(_.isAdmin)
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
    Act.secured.disabled.async { implicit request =>
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
    Act.secured.disabled.async { implicit request =>
      for {
        maybeUser             <- userRepository.get(userId)
        user                  <- maybeUser.liftTo[Future](UserNotFoundById(userId))
        companiesWithAccesses <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(request.identity)
        onlyAdminCompanies = companiesWithAccesses.filter(_.isAdmin)
        usersAccesses <- companyAccessRepository.getUserAccesses(onlyAdminCompanies.map(_.company.id), userId)
        _             <- usersAccesses.traverse(c => removeAccessFor(c.companyId, user, request.identity))
      } yield Ok(user.email.value)
    }

  private def removeAccessFor(companyId: UUID, user: User, requestBy: User) =
    for {
      _ <- companyAccessOrchestrator.removeAccess(companyId, user, requestBy)
    } yield ()

  def updateAccess(siret: String, userId: UUID) =
    Act.securedWithCompanyAccessBySiret(siret, adminLevelOnly = true).forbidImpersonation.async { implicit request =>
      request.body.asJson
        .map(json => (json \ "level").as[AccessLevel])
        .map(level =>
          for {
            user <- userRepository.get(userId)
            _ <- user
              .map(u => companyAccessRepository.createAccess(request.company.id, u.id, level))
              .getOrElse(Future.unit)
          } yield if (user.isDefined) Ok else NotFound
        )
        .getOrElse(Future.successful(NotFound))
    }

  def removeAccess(siret: String, userId: UUID) =
    Act.securedWithCompanyAccessBySiret(siret, adminLevelOnly = true).forbidImpersonation.async { implicit request =>
      for {
        maybeUser <- userRepository.get(userId)
        user      <- maybeUser.liftTo[Future](UserNotFoundById(userId))
        _         <- companyAccessOrchestrator.removeAccess(request.company.id, user, requestedBy = request.identity)
        // this operation may leave some reports assigned to this user, to which he doesn't have access anymore
        // in theory here we should find these reports and de-assign them
      } yield NoContent
    }

  private case class AccessInvitation(email: EmailAddress, level: AccessLevel)

  def sendInvitation(siret: String) =
    Act.securedWithCompanyAccessBySiret(siret, adminLevelOnly = true).forbidImpersonation.async(parse.json) {
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

  def listPendingTokens(siret: String) =
    Act.securedWithCompanyAccessBySiret(siret).allowImpersonation.async { implicit request =>
      accessesOrchestrator
        .listProPendingToken(request.company, request.identity)
        .map(tokens => Ok(Json.toJson(tokens)))
    }

  def removePendingToken(siret: String, tokenId: UUID) =
    Act.securedWithCompanyAccessBySiret(siret, adminLevelOnly = true).forbidImpersonation.async { implicit request =>
      for {
        token <- accessTokenRepository.getToken(request.company, tokenId)
        _     <- token.map(accessTokenRepository.invalidateToken).getOrElse(Future.unit)
      } yield if (token.isDefined) Ok else NotFound
    }

  def fetchTokenInfo(siret: String, token: String) = Act.public.standardLimit.async { _ =>
    accessesOrchestrator
      .fetchCompanyUserActivationToken(SIRET.fromUnsafe(siret), token)
      .map(token => Ok(Json.toJson(token)))
  }

  def sendActivationLink(siret: String) = Act.public.standardLimit.async(parse.json) { implicit request =>
    for {
      activationLinkRequest <- request.parseBody[ActivationLinkRequest]()
      _ <- companyAccessOrchestrator.sendActivationLink(SIRET.fromUnsafe(siret), activationLinkRequest)
    } yield Ok

  }

  private case class AcceptTokenRequest(token: String)

  def acceptToken(siret: String) = Act.secured.all.allowImpersonation.async(parse.json) { implicit request =>
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

  def getProFirstActivationCount(ticks: Option[Int]) =
    Act.secured.adminsAndReadonlyAndAgents.allowImpersonation.async(parse.empty) { _ =>
      accessesOrchestrator.proFirstActivationCount(ticks).map(x => Ok(Json.toJson(x)))
    }

}
