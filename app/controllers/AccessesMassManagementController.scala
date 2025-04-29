package controllers

import authentication.Authenticator
import controllers.error.AppError.CantPerformAction
import models.User
import models.access.AccessesMassManagement.MassManagementOperation.Remove
import models.access.AccessesMassManagement.MassManagementInputs
import models.access.AccessesMassManagement.MassManagementOperationSetAs
import models.access.AccessesMassManagement.MassManagementUsers
import models.company.CompanyWithAccess
import orchestrators._
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import repositories.accesstoken.AccessTokenRepositoryInterface
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
      proAccessTokensEmailed <- proAccessTokenOrchestrator
        .listProPendingTokensSentByEmail(siretsAndIds.map(_._2), req.identity)
      invitedEmails = proAccessTokensEmailed.flatMap(_.emailedTo).map(_.value).distinct
    } yield Ok(
      Json.toJson(
        MassManagementUsers(
          users,
          invitedEmails
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
      _ = checkInputs(inputs, allCompaniesOfPro, req.identity)
      (companiesToManage, usersToManage, emailsToManage) <- prepareDataToManage(inputs)
      _ <- inputs.operation match {
        case Remove =>
          for {
            _ <- companyAccessOrchestrator.removeAccessesIfExist(
              companiesToManage.map(_.id),
              usersToManage.map(_.id),
              req.identity
            )
            _ <- Future.sequence(emailsToManage.map { email =>
              proAccessTokenOrchestrator.invalidateInvitationsIfExist(companiesToManage, email)
            })
          } yield ()
        case operationSetAs: MassManagementOperationSetAs =>
          val desiredLevel = operationSetAs.desiredLevel
          for {
            _ <- Future.sequence(
              usersToManage.map(user =>
                proAccessTokenOrchestrator.addInvitedUserAndNotify(user, companiesToManage, desiredLevel)
              )
            )
            _ <- Future.sequence(
              emailsToManage.map(email =>
                proAccessTokenOrchestrator.sendInvitations(companiesToManage, email, desiredLevel)
              )
            )
          } yield ()
      }
    } yield Ok
  }

  private def checkInputs(
      inputs: MassManagementInputs,
      allCompaniesOfPro: List[CompanyWithAccess],
      requestedBy: User
  ): Future[Unit] = {
    val proManageableCompaniesIds = allCompaniesOfPro.filter(_.isAdmin).map(_.company.id)
    for {
      _ <-
        if (inputs.companiesIds.forall(proManageableCompaniesIds.contains)) {
          Future.unit
        } else {
          Future.failed(CantPerformAction)
        }
      _ <-
        if (
          inputs.users.usersIds.contains(
            requestedBy.id
          ) || (inputs.users.emailsToInvite ++ inputs.users.alreadyInvitedEmails).contains(requestedBy.email)
        ) {
          Future.failed(CantPerformAction)
        } else {
          Future.unit
        }
    } yield ()
  }

  private def prepareDataToManage(
      inputs: MassManagementInputs,
      allCompaniesOfPro: List[CompanyWithAccess]
  ) =
    // The user can select
    // - existing users
    // - emails that have already some invitation to some company
    // - emails manually typed, that at first glance should not belong to any existing user or invitation
    //    => but actually they could theoretically correspond to a user or invitation that the requesting user didn't know about
    //       if it was not on a company that he knows about
    // Thus we have to treat the "emailsToInvite" as something that could be a user or an already invited email
    for {

      users <- userOrchestrator.findAllByIdOrError(inputs.users.usersIds)
      emails =
        (inputs.users.alreadyInvitedEmails ++ inputs.users.emailsToInvite).map(EmailAddress.apply)
      usersFromEmails <- userOrchestrator.findByEmails(emails)
      usersToManage     = (users ++ usersFromEmails).distinctBy(_.id)
      emailsToManage    = emails.filterNot(email => usersFromEmails.exists(_.email == email))
      companiesToManage = allCompaniesOfPro.map(_.company).filter(x => inputs.companiesIds.contains(x.id))
    } yield (companiesToManage, usersToManage, emailsToManage)

}
