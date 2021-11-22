package controllers

import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppErrorTransformer.handleError
import models._
import orchestrators.AccessesOrchestrator
import orchestrators.CompaniesVisibilityOrchestrator
import play.api.Logger
import play.api.libs.json._
import play.api.libs.json.Json
import repositories._
import utils.EmailAddress
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyAccessController @Inject() (
    val userRepository: UserRepository,
    val companyRepository: CompanyRepository,
    val accessTokenRepository: AccessTokenRepository,
    val accessesOrchestrator: AccessesOrchestrator,
    val companyVisibilityOrch: CompaniesVisibilityOrchestrator,
    val silhouette: Silhouette[AuthEnv]
)(implicit ec: ExecutionContext)
    extends BaseCompanyController {

  val logger: Logger = Logger(this.getClass())

  def listAccesses(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    accessesOrchestrator
      .listAccesses(request.company, request.identity)
      .map(res => Ok(Json.toJson(res)))
      .recover { case err =>
        handleError(err)
      }
  }

  def myCompanies = SecuredAction.async { implicit request =>
    companyRepository
      .fetchCompaniesWithLevel(request.identity)
      .map(companies => Ok(Json.toJson(companies)))
  }

  def updateAccess(siret: String, userId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async {
    implicit request =>
      request.body.asJson
        .map(json => (json \ "level").as[AccessLevel])
        .map(level =>
          for {
            user <- userRepository.get(userId)
            _ <- user
              .map(u => companyRepository.createUserAccess(request.company.id, u.id, level))
              .getOrElse(Future(()))
          } yield if (user.isDefined) Ok else NotFound
        )
        .getOrElse(Future(NotFound))
  }

  def removeAccess(siret: String, userId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async {
    implicit request =>
      for {
        user <- userRepository.get(userId)
        _ <- user
          .map(u => companyRepository.createUserAccess(request.company.id, u.id, AccessLevel.NONE))
          .getOrElse(Future(()))
      } yield if (user.isDefined) Ok else NotFound
  }

  case class AccessInvitation(email: EmailAddress, level: AccessLevel)

  def sendInvitation(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async(parse.json) {
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

  case class AccessInvitationList(email: EmailAddress, level: AccessLevel, sirets: List[SIRET])

  def sendGroupedInvitations = SecuredAction(WithRole(UserRoles.Admin)).async(parse.json) { implicit request =>
    implicit val reads = Json.reads[AccessInvitationList]
    request.body
      .validate[AccessInvitationList]
      .fold(
        errors => {
          logger.error(s"$errors")
          Future.successful(BadRequest(JsError.toJson(errors)))
        },
        invitations =>
          accessesOrchestrator
            .addUserOrInvite(invitations.sirets, invitations.email, invitations.level, Some(request.identity))
            .map(_ => Ok)
      )
  }

  def listPendingTokens(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      tokens <- accessTokenRepository.fetchPendingTokens(request.company)
    } yield Ok(
      Json.toJson(
        tokens.map(token =>
          Json.obj(
            "id" -> token.id.toString,
            "level" -> token.companyLevel.get.value,
            "emailedTo" -> token.emailedTo,
            "expirationDate" -> token.expirationDate
          )
        )
      )
    )
  }

  def removePendingToken(siret: String, tokenId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async {
    implicit request =>
      for {
        token <- accessTokenRepository.getToken(request.company, tokenId)
        _ <- token.map(accessTokenRepository.invalidateToken(_)).getOrElse(Future(()))
      } yield if (token.isDefined) Ok else NotFound
  }

  def fetchTokenInfo(siret: String, token: String) = UnsecuredAction.async { _ =>
    accessesOrchestrator
      .fetchCompanyUserActivationToken(SIRET(siret), token)
      .map(token => Ok(Json.toJson(token)))
      .recover { case err =>
        handleError(err)
      }
  }

  case class ActivationLinkRequest(token: String, email: EmailAddress)

  def sendActivationLink(siret: String) = UnsecuredAction.async(parse.json) { implicit request =>
    implicit val reads = Json.reads[ActivationLinkRequest]
    request.body
      .validate[ActivationLinkRequest]
      .fold(
        errors => Future.successful(BadRequest(JsError.toJson(errors))),
        activationLinkRequest =>
          for {
            company <- companyRepository.findBySiret(SIRET(siret))
            isValid <- company
              .map(c =>
                accessTokenRepository
                  .fetchActivationCode(c)
                  .map(_.map(_ == activationLinkRequest.token).getOrElse(false))
              )
              .getOrElse(Future(false))
            sent <-
              if (isValid)
                accessesOrchestrator
                  .addUserOrInvite(company.get, activationLinkRequest.email, AccessLevel.ADMIN, None)
                  .map(_ => true)
              else Future(false)
          } yield if (sent) Ok else NotFound
      )
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
            company <- companyRepository.findBySiret(SIRET(siret))
            token <- company
              .map(
                accessTokenRepository
                  .findToken(_, acceptTokenRequest.token)
                  .map(
                    _.filter(
                      _.emailedTo.filter(email => email != request.identity.email).isEmpty
                    )
                  )
              )
              .getOrElse(Future(None))
            applied <- token
              .map(t =>
                accessTokenRepository
                  .applyCompanyToken(t, request.identity)
              )
              .getOrElse(Future(false))
          } yield if (applied) Ok else NotFound
      )
  }

  def reportOverCompanyAccessRate(ticks: Option[Int]) = SecuredAction.async(parse.empty) { _ =>
    accessesOrchestrator.reportOverCompanyAccessRate(ticks).map(x => Ok(Json.toJson(x))).recover { case err =>
      handleError(err)
    }
  }

}
