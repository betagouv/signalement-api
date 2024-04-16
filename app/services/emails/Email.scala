package services.emails

import models.report.Report
import play.api.libs.mailer.Attachment
import services.AttachmentService
import utils.EmailAddress
import utils.FrontRoute

trait Email {
  val recipients: Seq[EmailAddress]
  val subject: String
  def getBody: (FrontRoute, EmailAddress) => String
  def getAttachements: AttachmentService => Seq[Attachment] = _.defaultAttachments
}

sealed trait ProFilteredEmail extends Email
trait ProFilteredEmailSingleReport extends ProFilteredEmail {
  val report: Report
}
trait ProFilteredEmailMultipleReport extends ProFilteredEmail {
  val reports: List[Report]
}
