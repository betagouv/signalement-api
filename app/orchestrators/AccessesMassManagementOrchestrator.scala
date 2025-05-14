package orchestrators

import controllers.error.AppError.CantPerformAction
import models.User
import models.access.AccessesMassManagement.MassManagementOperation.Remove
import models.access.AccessesMassManagement.MassManagementInputs
import models.access.AccessesMassManagement.MassManagementOperationSetAs
import models.access.AccessesMassManagement.MassManagementUsers
import models.company.Company
import models.company.CompanyWithAccess
import play.api.Logger
import repositories.event.EventRepositoryInterface
import services.EventsBuilder.accessesMassManagementEvent
import utils.EmailAddress

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class AccessesMassManagementOrchestrator(
    companiesVisibilityOrchestrator: CompaniesVisibilityOrchestrator,
    proAccessTokenOrchestrator: ProAccessTokenOrchestrator,
    companyAccessOrchestrator: CompanyAccessOrchestrator,
    userOrchestrator: UserOrchestrator,
    eventRepository: EventRepositoryInterface
)(implicit val executionContext: ExecutionContext) {

  val logger = Logger(this.getClass)

  def getUsersAndEmailsKnownToPro(user: User) =
    for {
      companies <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(user)
      siretsAndIds = companies.map(_.company).map(c => c.siret -> c.id)
      mapOfUsers <- companiesVisibilityOrchestrator.fetchUsersOfCompanies(siretsAndIds)
      users = mapOfUsers.values.flatten.toList.distinctBy(_.id)
      proAccessTokensEmailed <- proAccessTokenOrchestrator
        .listProPendingTokensSentByEmail(siretsAndIds.map(_._2), user)
      invitedEmails = proAccessTokensEmailed.flatMap(_.emailedTo).map(_.value).distinct
    } yield MassManagementUsers(
      users,
      invitedEmails
    )

  def massManageAccesses(inputs: MassManagementInputs, requestedBy: User): Future[Unit] = {
    logger.info(
      s"MassManagement operation requested : ${inputs.toStringForLogs()}"
    )
    for {
      allCompaniesOfPro <- companiesVisibilityOrchestrator.fetchVisibleCompaniesList(requestedBy)
      _                 <- Future(checkIsAllowed(inputs, allCompaniesOfPro, requestedBy))
      (companiesToManage, usersToManage, emailsToManage) <- prepareDataToManage(inputs, allCompaniesOfPro)
      _ = logger.info(
        s"Will actually apply to companies ${companiesToManage.map(_.id)}, users ${usersToManage
            .map(_.id)}, emails ${emailsToManage}"
      )
      _ <- inputs.operation match {
        case Remove =>
          for {
            _ <- companyAccessOrchestrator.removeAccessesIfExist(
              companiesToManage.map(_.id),
              usersToManage,
              requestedBy
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
                proAccessTokenOrchestrator.addInvitedUserIfNeededAndNotify(user, companiesToManage, desiredLevel)
              )
            )
            _ <- Future.sequence(
              emailsToManage.map(email =>
                proAccessTokenOrchestrator.sendInvitations(companiesToManage, email, desiredLevel)
              )
            )
          } yield ()

      }
      _ <- eventRepository.create(
        accessesMassManagementEvent(
          inputs.operation,
          companiesToManage,
          usersToManage,
          emailsToManage,
          requestedBy
        )
      )
    } yield ()
  }

  private def checkIsAllowed(
      inputs: MassManagementInputs,
      allCompaniesOfPro: List[CompanyWithAccess],
      requestedBy: User
  ): Unit = {
    val proManageableCompaniesIds = allCompaniesOfPro.filter(_.isAdmin).map(_.company.id)
    if (
      !inputs.companiesIds.forall(proManageableCompaniesIds.contains) ||
      inputs.users.usersIds.contains(
        requestedBy.id
      ) || (inputs.users.emailsToInvite ++ inputs.users.alreadyInvitedEmails).contains(requestedBy.email)
    ) {
      throw CantPerformAction
    }
  }

  private def prepareDataToManage(
      inputs: MassManagementInputs,
      allCompaniesOfPro: List[CompanyWithAccess]
  ): Future[(List[Company], List[User], List[EmailAddress])] =
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
