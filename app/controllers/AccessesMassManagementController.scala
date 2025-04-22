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
import orchestrators.CompaniesVisibilityOrchestrator
import orchestrators.CompanyOrchestrator
import orchestrators.ProAccessTokenOrchestrator
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface

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
      proCompanies <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(req.identity)
      proManageableCompaniesIds = proCompanies.filter(_.isAdmin).map(_.company.id)
      _ <-
        if (inputs.companiesIds.forall(proManageableCompaniesIds.contains)) { Future.unit }
        else { Future.failed(CantPerformAction) }
      // parmi les users aucun ne doit etre lui-meme
      _ <-
        if (inputs.users.usersIds.contains(req.identity.id)) { Future.failed(CantPerformAction) }
        else { Future.unit }
      _ <- inputs.operation match {
        case Remove =>
          for {
            _ <- companyAccessRepository.removeAccessesIfExist(inputs.companiesIds, inputs.users.usersIds)
            _ <- accessTokenRepository.invalidateCompanyJoinAccessTokens(
              inputs.companiesIds,
              inputs.users.alreadyInvitedTokenIds
            )
          } yield ()

        case SetMember | SetAdmin =>
          for {
            // TODO si set as member/admin
            //   set les accesses à membre/admin sur ces entreprises pour ces users ids
            //   set les accesses à membre/admin de ces invited token ids sur ces entreprises ??? si possible
            //   inviter ces emailsToInvite avec ces accesses membre/admin sur ces entreprises ???
            _ <- Future.unit
          } yield ()
          ???
      }
    } yield ???
    Future.successful(Ok)
  }

}
