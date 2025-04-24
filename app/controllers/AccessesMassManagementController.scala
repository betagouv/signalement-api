package controllers

import authentication.Authenticator
import controllers.error.AppError.CantPerformAction
import controllers.error.ForbiddenError
import models.User
import models.access.AccessesMassManagement.MassManagementInputs
import models.access.AccessesMassManagement.MassManagementOperation.Remove
import models.access.AccessesMassManagement.MassManagementOperation.SetAdmin
import models.access.AccessesMassManagement.MassManagementOperation.SetMember
import models.access.AccessesMassManagement.MassManagementUsers
import models.auth.UserLogin
import models.company.AccessLevel
import models.company.CompanyAccessCreationInput
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import orchestrators.ProAccessTokenOrchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import utils.EmailAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AccessesMassManagementController(
    val companyOrchestrator: CompanyOrchestrator,
    val companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    proAccessTokenOrchestrator: ProAccessTokenOrchestrator,
    companyAccessRepository: CompanyAccessRepositoryInterface,
    accessTokenRepository: AccessTokenRepositoryInterface,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit
    val ec: ExecutionContext
) extends BaseCompanyController(authenticator, controllerComponents) {

  def getCompaniesOfPro = Act.secured.pros.allowImpersonation.async { req =>
    for {
      companies <- companiesVisibilityOrchestrator.fetchVisibleCompanies(req.identity)
    } yield Ok(Json.toJson(companies))
  }

  def getUsersOfPro = Act.secured.pros.allowImpersonation.async { req =>
    for {
      companies <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(req.identity)
      siretsAndIds = companies.map(_.company).map(c => c.siret -> c.id)
      mapOfUsers <- companiesVisibilityOrchestrator.fetchUsersWithHeadOffices(siretsAndIds)
      users = mapOfUsers.values.flatten.toList.distinctBy(_.id)
      invitedByEmail <- proAccessTokenOrchestrator
        .listProPendingTokensSentByEmail(siretsAndIds.map(_._2), req.identity)
    } yield Ok(
      Json.toJson(
        MassManagementUsers(
          users,
          invitedByEmail
        )
      )
    )
  }

  def manageAccesses = Act.secured.pros.forbidImpersonation.async(parse.json) { req =>
    for {
      inputs <- req.parseBody[MassManagementInputs]()
      // le pro doit avoir acces ADMIN à ces entreprises
      allCompaniesOfPro <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(req.identity)
      proManageableCompaniesIds = allCompaniesOfPro.filter(_.isAdmin).map(_.company.id)
      _ <-
        if (inputs.companiesIds.forall(proManageableCompaniesIds.contains)) { Future.unit }
        else { Future.failed(CantPerformAction) }
      // parmi les users aucun ne doit etre lui-meme
      _ <-
        if (inputs.users.usersIds.contains(req.identity.id)) { Future.failed(CantPerformAction) }
        else { Future.unit }
      inputCompanies = allCompaniesOfPro.map(_.company).filter(x => inputs.companiesIds.contains(x.id))
      _ <- inputs.operation match {
        case Remove =>
          for {
            // TODO il y a un event à créer normalement
            _ <- companyAccessRepository.removeAccessesIfExist(inputs.companiesIds, inputs.users.usersIds)
            _ <- accessTokenRepository.invalidateCompanyJoinAccessTokens(
              inputs.companiesIds,
              inputs.users.alreadyInvitedTokenIds
            )
          } yield ()

        case SetMember | SetAdmin =>
          val desiredLevel = inputs.operation match {
            case SetMember => AccessLevel.MEMBER
            case SetAdmin  => AccessLevel.ADMIN
          }
          val accessesToCreate = for {
            companyId <- inputs.companiesIds
            userId    <- inputs.users.usersIds
          } yield CompanyAccessCreationInput(
            companyId = companyId,
            userId = userId,
            desiredLevel
          )
          for {
            // TODO il y a un event à créer peut-être ?
            _ <- companyAccessRepository.createMultipleUserAccesses(accessesToCreate)

            // TODO set les accesses à membre/admin de ces invited token ids sur ces entreprises ??? si possible
            // ....

            // on invite les emailsToInvite
            _ <- Future.sequence(
              inputs.users.emailsToInvite.map(email =>
                proAccessTokenOrchestrator.sendInvitations(inputCompanies, EmailAddress(email), desiredLevel)
              )
            )
            _ <- Future.unit

            _ <- Future.unit
          } yield ()
          ???
      }
    } yield ???
    Future.successful(Ok)
  }

}
