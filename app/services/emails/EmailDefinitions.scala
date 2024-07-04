package services.emails

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import play.api.i18n.MessagesApi
import services.emails.EmailDefinitionsAdmin.AdminAccessLink
import services.emails.EmailDefinitionsAdmin.AdminProbeTriggered
import services.emails.EmailDefinitionsConsumer.ConsumerProEngagementReview
import services.emails.EmailDefinitionsConsumer.ConsumerProResponseNotification
import services.emails.EmailDefinitionsConsumer.ConsumerProResponseNotificationOnAdminCompletion
import services.emails.EmailDefinitionsConsumer.ConsumerReportAcknowledgment
import services.emails.EmailDefinitionsConsumer.ConsumerReportClosedNoAction
import services.emails.EmailDefinitionsConsumer.ConsumerReportClosedNoReading
import services.emails.EmailDefinitionsConsumer.ConsumerReportDeletionConfirmation
import services.emails.EmailDefinitionsConsumer.ConsumerReportReadByProNotification
import services.emails.EmailDefinitionsConsumer.ConsumerValidateEmail
import services.emails.EmailDefinitionsDggcrf.DgccrfAgentAccessLink
import services.emails.EmailDefinitionsDggcrf.DgccrfDangerousProductReportNotification
import services.emails.EmailDefinitionsDggcrf.DgccrfImportantReportNotification
import services.emails.EmailDefinitionsDggcrf.DgccrfInactiveAccount
import services.emails.EmailDefinitionsDggcrf.DgccrfReportNotification
import services.emails.EmailDefinitionsDggcrf.DgccrfValidateEmail
import services.emails.EmailDefinitionsPro.ProCompaniesAccessesInvitations
import services.emails.EmailDefinitionsPro.ProCompanyAccessInvitation
import services.emails.EmailDefinitionsPro.ProNewCompaniesAccesses
import services.emails.EmailDefinitionsPro.ProNewCompanyAccess
import services.emails.EmailDefinitionsPro.ProNewReportNotification
import services.emails.EmailDefinitionsPro.ProReportAssignedNotification
import services.emails.EmailDefinitionsPro.ProReportReOpeningNotification
import services.emails.EmailDefinitionsPro.ProReportsReadReminder
import services.emails.EmailDefinitionsPro.ProReportsUnreadReminder
import services.emails.EmailDefinitionsPro.ProResponseAcknowledgment
import services.emails.EmailDefinitionsPro.ProResponseAcknowledgmentOnAdminCompletion
import services.emails.EmailDefinitionsVarious.ResetPassword
import services.emails.EmailDefinitionsVarious.UpdateEmailAddress
import utils.EmailAddress

trait EmailDefinition {
  val category: EmailCategory

  def examples: Seq[(String, (EmailAddress, MessagesApi) => BaseEmail)]

  // Each EmailDefinition object should also define an Email case class
  // This can't be enforced sadly

}

object EmailDefinitions {
  val allEmailDefinitions: Seq[EmailDefinition] = Seq(
    // Various
    ResetPassword,
    UpdateEmailAddress,
    // Admin
    AdminAccessLink,
    AdminProbeTriggered,
    // Dgccrf
    DgccrfAgentAccessLink,
    DgccrfInactiveAccount,
    DgccrfDangerousProductReportNotification,
    DgccrfImportantReportNotification,
    DgccrfReportNotification,
    DgccrfValidateEmail,
    // Pro
    ProCompanyAccessInvitation,
    ProCompaniesAccessesInvitations,
    ProNewCompanyAccess,
    ProNewCompaniesAccesses,
    ProResponseAcknowledgment,
    ProResponseAcknowledgmentOnAdminCompletion,
    ProNewReportNotification,
    ProReportReOpeningNotification,
    ProReportsReadReminder,
    ProReportsUnreadReminder,
    ProReportAssignedNotification,
    // Consumer
    ConsumerProEngagementReview,
    ConsumerReportDeletionConfirmation,
    ConsumerReportAcknowledgment,
    ConsumerReportReadByProNotification,
    ConsumerProResponseNotification,
    ConsumerProResponseNotificationOnAdminCompletion,
    ConsumerReportClosedNoReading,
    ConsumerReportClosedNoAction,
    ConsumerValidateEmail
  )
}
sealed trait EmailCategory extends EnumEntry

object EmailCategory extends PlayEnum[EmailCategory] {
  override def values: IndexedSeq[EmailCategory] = findValues

  case object Various extends EmailCategory

  case object Admin extends EmailCategory

  case object Dgccrf   extends EmailCategory
  case object Pro      extends EmailCategory
  case object Consumer extends EmailCategory

}
