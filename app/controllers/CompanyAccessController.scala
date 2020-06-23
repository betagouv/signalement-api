package controllers

import javax.inject.{Inject, Singleton}
import java.util.UUID
import repositories._
import models._
import orchestrators.AccessesOrchestrator
import play.api.libs.json._
import play.api.Logger
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv
import utils.{EmailAddress, SIRET}


@Singleton
class CompanyAccessController @Inject()(
                                val userRepository: UserRepository,
                                val companyRepository: CompanyRepository,
                                val accessTokenRepository: AccessTokenRepository,
                                val accessesOrchestrator: AccessesOrchestrator,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {

  val logger: Logger = Logger(this.getClass())
  def listAccesses(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      userAccesses <- companyRepository.fetchUsersWithLevel(request.company)
    } yield Ok(Json.toJson(userAccesses.map{
      case (user, level) => Map(
          "userId"    -> user.id.toString,
          "firstName" -> user.firstName,
          "lastName"  -> user.lastName,
          "email"     -> user.email.value,
          "level"     -> level.value
      )
    }))
  }

  def myCompanies = SecuredAction.async { implicit request =>
    for {
      companyAccesses <- companyRepository.fetchCompaniesWithLevel(request.identity)
    } yield Ok(Json.toJson(companyAccesses.map{
      case (company, level) => Map(
          "companySiret"      -> company.siret.value,
          "companyName"       -> company.name,
          "companyAddress"    -> company.address.value,
          "level"             -> level.value
      )
    }))
  }

  def updateAccess(siret: String, userId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    request.body.asJson.map(json => (json \ "level").as[AccessLevel]).map(level =>
      for {
        user <- userRepository.get(userId)
        _    <- user.map(u => companyRepository.setUserLevel(request.company, u, level)).getOrElse(Future(Unit))
      } yield if (user.isDefined) Ok else NotFound
    ).getOrElse(Future(NotFound))
  }

  def removeAccess(siret: String, userId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      user <- userRepository.get(userId)
      _    <- user.map(u => companyRepository.setUserLevel(request.company, u, AccessLevel.NONE)).getOrElse(Future(Unit))
    } yield if (user.isDefined) Ok else NotFound
  }

  case class AccessInvitation(email: EmailAddress, level: AccessLevel)

  def sendInvitation(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async(parse.json) { implicit request =>
    implicit val reads = Json.reads[AccessInvitation]
    request.body.validate[AccessInvitation].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      invitation => accessesOrchestrator
                    .addUserOrInvite(request.company, invitation.email, invitation.level, Some(request.identity))
                    .map(_ => Ok)
    )
  }

  def listPendingTokens(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      tokens <- accessTokenRepository.fetchPendingTokens(request.company)
    } yield Ok(Json.toJson(tokens.map(token =>
      Json.obj(
          "id"              -> token.id.toString,
          "level"           -> token.companyLevel.get.value,
          "emailedTo"       -> token.emailedTo,
          "expirationDate"  -> token.expirationDate
      )
    )))
  }

  def removePendingToken(siret: String, tokenId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      token <- accessTokenRepository.getToken(request.company, tokenId)
      _ <- token.map(accessTokenRepository.invalidateToken(_)).getOrElse(Future(Unit))
    } yield {if (token.isDefined) Ok else NotFound}
  }

  def fetchTokenInfo(siret: String, token: String) = UnsecuredAction.async { implicit request =>
    for {
      company <- companyRepository.findBySiret(SIRET(siret))
      token   <- company.map(accessTokenRepository.findToken(_, token))
                        .getOrElse(Future(None))
    } yield token.flatMap(t => company.map(c => 
      Ok(Json.toJson(TokenInfo(t.token, t.kind, Some(c.siret), t.emailedTo)))
    )).getOrElse(NotFound)
  }

  case class ActivationLinkRequest(token: String, email: EmailAddress)

  def sendActivationLink(siret: String) = UnsecuredAction.async(parse.json) { implicit request =>
    implicit val reads = Json.reads[ActivationLinkRequest]
    request.body.validate[ActivationLinkRequest].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      activationLinkRequest =>
        for {
          company <- companyRepository.findBySiret(SIRET(siret))
          isValid <- company.map(c =>
                        accessTokenRepository
                          .fetchActivationCode(c)
                          .map(_.map(_ == activationLinkRequest.token).getOrElse(false))
                      )
                      .getOrElse(Future(false))
          sent <- if (isValid)
                    accessesOrchestrator.addUserOrInvite(company.get, activationLinkRequest.email, AccessLevel.ADMIN, None).map(_ => true)
                  else Future(false)
        } yield if (sent) Ok else NotFound
    )
  }

  case class AcceptTokenRequest(token: String)

  def acceptToken(siret: String) = SecuredAction.async(parse.json) { implicit request =>
    implicit val reads = Json.reads[AcceptTokenRequest]
    request.body.validate[AcceptTokenRequest].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      acceptTokenRequest =>
        for {
          company <- companyRepository.findBySiret(SIRET(siret))
          token   <- company.map(
                      accessTokenRepository
                        .findToken(_, acceptTokenRequest.token)
                        .map(
                          _.filter(
                            _.emailedTo.filter(email => email != request.identity.email).isEmpty
                          )
                        )
                      )
                      .getOrElse(Future(None))
          applied <- token.map(t =>
                      accessTokenRepository
                      .applyCompanyToken(t, request.identity)
                    ).getOrElse(Future(false))
        } yield if (applied) Ok else NotFound
    )
  }
}
