package controllers

import authentication.Authenticator
import controllers.error.AppError.CantPerformAction
import models.User
import models.access.AccessesMassManagement.MassManagementOperation.Remove
import models.access.AccessesMassManagement.MassManagementInputs
import models.access.AccessesMassManagement.MassManagementOperationSetAs
import models.access.AccessesMassManagement.MassManagementUsers
import orchestrators._
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
    companyAccessOrchestrator: CompanyAccessOrchestrator,
    userOrchestrator: UserOrchestrator,
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
        .map(_.distinctBy(_.emailedTo))
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
    // TODO tester à peu près que j'ai bien compris chacun des cas
    // TODO add a global log and event
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
            _ <- companyAccessOrchestrator.removeAccessesIfExist(
              inputs.companiesIds,
              inputs.users.usersIds,
              req.identity
            )
            _ <- accessTokenRepository.invalidateCompanyJoinAccessTokens(
              inputs.companiesIds,
              inputs.users.alreadyInvitedTokenIds
            )
          } yield ()
        case operationSetAs: MassManagementOperationSetAs =>
          val desiredLevel = operationSetAs.desiredLevel
          for {
            users <- userOrchestrator.findAllByIdOrError(inputs.users.usersIds)
            _ <- Future.sequence(
              users.map(user => proAccessTokenOrchestrator.addInvitedUserAndNotify(user, inputCompanies, desiredLevel))
            )
            _ <- Future.sequence(
              inputs.users.alreadyInvitedTokenIds.map(tokenId =>
                proAccessTokenOrchestrator.extendInvitationToAdditionalCompanies(tokenId, inputCompanies, desiredLevel)
              )
            )
            _ <- Future.sequence(
              // TODO what if the user already existed as a full user but our requested user didn't know it ?
              inputs.users.emailsToInvite.map(email =>
                proAccessTokenOrchestrator.sendInvitations(inputCompanies, EmailAddress(email), desiredLevel)
              )
            )
          } yield ()
      }
    } yield Ok
  }

}
