package services

import com.google.inject.Inject
import models.Event
import models.Report
import models.ReportFile
import models.UserRole
import play.api.Environment
import play.api.libs.mailer.Attachment
import play.api.libs.mailer.AttachmentData
import play.api.libs.mailer.AttachmentFile
import utils.FrontRoute

class AttachementService @Inject() (environment: Environment, pdfService: PDFService, frontRoute: FrontRoute) {

  val defaultAttachments: Seq[Attachment] = Seq(
    AttachmentFile(
      "logo-signal-conso.png",
      environment.getFile("/appfiles/logo-signal-conso.png"),
      contentId = Some("logo-signalconso")
    ),
    AttachmentFile(
      "logo-marianne.png",
      environment.getFile("/appfiles/logo-marianne.png"),
      contentId = Some("logo-marianne")
    )
  )

  def attachmentSeqForWorkflowStepN(n: Int): Seq[Attachment] = defaultAttachments ++ Seq(
    AttachmentFile(
      s"schemaSignalConso-Etape$n.png",
      environment.getFile(s"/appfiles/schemaSignalConso-Etape$n.png"),
      contentId = Some(s"schemaSignalConso-Etape$n")
    )
  )

  def needWorkflowSeqForWorkflowStepN(n: Int, report: Report): Seq[Attachment] =
    attachmentSeqForWorkflowStepN(n).filter(_ => report.needWorkflowAttachment())

  def reportAcknowledgmentAttachement(
      report: Report,
      event: Event,
      files: Seq[ReportFile],
      userRole: Option[UserRole]
  ): Seq[Attachment] =
    needWorkflowSeqForWorkflowStepN(2, report) ++
      Seq(
        AttachmentData(
          "Signalement.pdf",
          pdfService.getPdfData(
            views.html.pdfs.report(report, Seq((event, None)), None, Seq.empty, files)(frontRoute, userRole)
          ),
          "application/pdf"
        )
      ).filter(_ => report.isContractualDispute() && report.companyId.isDefined)

}
