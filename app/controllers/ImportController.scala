package controllers

import com.mohiva.play.silhouette.api.Silhouette
import controllers.error.AppError.EmptyEmails
import models.UserRole
import models.company.AccessLevel
import models.company.Company
import orchestrators.ProAccessTokenOrchestrator
import orchestrators.UserOrchestratorInterface
import play.api.libs.json.Json
import play.api.libs.json.OFormat
import play.api.mvc.ControllerComponents
import repositories.company.CompanyRepositoryInterface
import tasks.company.CompanySearchResult
import tasks.company.CompanySyncServiceInterface
import utils.EmailAddress
import utils.SIREN
import utils.SIRET
import utils.silhouette.auth.AuthEnv
import utils.silhouette.auth.WithRole

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

case class ImportInput(sirets: List[SIRET], emails: List[EmailAddress])

object ImportInput {
  implicit val test: OFormat[ImportInput] = Json.format[ImportInput]
}

class ImportController(
    companyRepository: CompanyRepositoryInterface,
    companySyncService: CompanySyncServiceInterface,
    userOrchestrator: UserOrchestratorInterface,
    proAccessTokenOrchestrator: ProAccessTokenOrchestrator,
    val silhouette: Silhouette[AuthEnv],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(controllerComponents) {

  private def sameSiren(sirets: List[SIRET], siren: SIREN) = sirets.forall(siret => SIREN.fromSIRET(siret) == siren)

  private def validateInput(input: ImportInput) =
    (input.emails, input.sirets) match {
      case (_, Nil) => Future.failed(EmptyEmails)
      case (Nil, _) => Future.failed(EmptyEmails)
      case (_, siret :: sirets) =>
        if (sameSiren(sirets, SIREN.fromSIRET(siret))) Future.successful(()) else Future.failed(EmptyEmails)

    }

  private def toCompany(c: CompanySearchResult) =
    Company(
      siret = c.siret,
      name = c.name.getOrElse(""),
      address = c.address,
      activityCode = c.activityCode,
      isHeadOffice = c.isHeadOffice,
      isOpen = c.isOpen,
      isPublic = c.isPublic
    )

  def importUsers = SecuredAction(WithRole(UserRole.Admin)).async(parse.json) { implicit request =>
    for {
      importInput <- request.parseBody[ImportInput]()
      _ <- validateInput(importInput)
      existingCompanies <- companyRepository.findBySirets(importInput.sirets)
      missingSirets = importInput.sirets.diff(existingCompanies.map(_.siret))
      missingCompanies <- companySyncService.companiesBySirets(missingSirets)
      createdCompanies <- Future.sequence(
        missingCompanies.map(c => companyRepository.getOrCreate(c.siret, toCompany(c)))
      )
      existingUsers <- userOrchestrator.list(importInput.emails)
      missingUsers = importInput.emails.diff(existingUsers.map(_.email))
      allCompanies = existingCompanies ++ createdCompanies
      _ <- Future.sequence(
        missingUsers.map(email => proAccessTokenOrchestrator.sendInvitations(allCompanies, email, AccessLevel.ADMIN))
      )
      _ <- Future.sequence(
        existingUsers.map(user =>
          proAccessTokenOrchestrator.addInvitedUserAndNotify(user, allCompanies, AccessLevel.ADMIN)
        )
      )
    } yield NoContent
  }
}
