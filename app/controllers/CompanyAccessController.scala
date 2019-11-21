package controllers

import javax.inject.{Inject, Singleton}
import java.util.UUID
import repositories._
import models._
import orchestrators.CompanyAccessOrchestrator
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import com.mohiva.play.silhouette.api.Silhouette
import utils.silhouette.auth.AuthEnv
import utils.EmailAddress


@Singleton
class CompanyAccessController @Inject()(
                                val userRepository: UserRepository,
                                val companyRepository: CompanyRepository,
                                val companyAccessRepository: CompanyAccessRepository,
                                val companyAccessOrchestrator: CompanyAccessOrchestrator,
                                val silhouette: Silhouette[AuthEnv]
                              )(implicit ec: ExecutionContext)
 extends BaseCompanyController {

  def listAccesses(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      userAccesses <- companyAccessRepository.fetchUsersWithLevel(request.company)
    } yield Ok(Json.toJson(userAccesses.map{
      case (user, level) => Map(
          "userId"    -> user.id.toString,
          "firstName" -> user.firstName.getOrElse("—"),
          "lastName"  -> user.lastName.getOrElse("—"),
          "email"     -> user.email.map(_.value).getOrElse("—"),
          "level"     -> level.value
      )
    }))
  }

  def updateAccess(siret: String, userId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    request.body.asJson.map(json => (json \ "level").as[AccessLevel]).map(level =>
      for {
        user <- userRepository.get(userId)
        _    <- user.map(u => companyAccessRepository.setUserLevel(request.company, u, level)).getOrElse(Future(Unit))
      } yield if (user.isDefined) Ok else NotFound
    ).getOrElse(Future(NotFound))
  }

  def removeAccess(siret: String, userId: UUID) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      user <- userRepository.get(userId)
      _    <- user.map(u => companyAccessRepository.setUserLevel(request.company, u, AccessLevel.NONE)).getOrElse(Future(Unit))
    } yield if (user.isDefined) Ok else NotFound
  }

  case class AccessInvitation(email: EmailAddress, level: AccessLevel)

  def sendInvitation(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async(parse.json) { implicit request =>
    implicit val reads = Json.reads[AccessInvitation]
    request.body.validate[AccessInvitation].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      invitation => companyAccessOrchestrator
                    .addUserOrInvite(request.company, invitation.email, invitation.level, request.identity)
                    .map(_ => Ok)
    )
  }

  def listPendingTokens(siret: String) = withCompany(siret, List(AccessLevel.ADMIN)).async { implicit request =>
    for {
      tokens <- companyAccessRepository.fetchPendingTokens(request.company)
    } yield Ok(Json.toJson(tokens.map(token =>
      Json.obj(
          "id"              -> token.id.toString,
          "level"           -> token.level.value,
          "emailedTo"       -> token.emailedTo,
          "expirationDate"  -> token.expirationDate
      )
    )))
  }

  def fetchTokenInfo(siret: String, token: String) = UnsecuredAction.async { implicit request =>
    for {
      company <- companyRepository.findBySiret(siret)
      token   <- company.map(companyAccessRepository.findToken(_, token))
                        .getOrElse(Future(None))
    } yield token.flatMap(t => company.map(c => 
      Ok(Json.toJson(TokenInfo(t.token, c.siret, t.emailedTo)))
    )).getOrElse(NotFound)
  }
}
