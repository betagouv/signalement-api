package services

import models.company.Company
import models.event.Event
import models.report.Report
import models.report.ReportFile
import models.report.ReportFileApi
import play.api.Environment
import play.api.i18n.MessagesProvider
import play.api.libs.mailer.Attachment
import play.api.libs.mailer.AttachmentData
import play.api.libs.mailer.AttachmentFile
import services.AttachmentService.schemaSignalConsoStep
import utils.FrontRoute

import java.util.Locale

object AttachmentService {
  def schemaSignalConsoStep(lang: Locale, step: Int) =
    if (lang == Locale.ENGLISH) {
      s"schemaSignalConso-Step$step"
    } else {
      s"schemaSignalConso-Etape$step"
    }
}

class AttachmentService(environment: Environment, pdfService: PDFService, frontRoute: FrontRoute) {

  val defaultAttachments: Seq[Attachment] = Seq(
    AttachmentFile(
      "logo-signal-conso.png",
      environment.getFile("/appfiles/logo-signal-conso.png"),
      contentId = Some("logo-signalconso")
    ),
    AttachmentFile(
      "logo-marianne.jpg",
      environment.getFile("/appfiles/logo-marianne.jpg"),
      contentId = Some("logo-marianne")
    )
  )

  def attachmentSeqForWorkflowStepN(n: Int, lang: Locale): Seq[Attachment] = defaultAttachments ++ Seq(
    AttachmentFile(
      s"${schemaSignalConsoStep(lang, n)}.png",
      environment.getFile(s"/appfiles/${schemaSignalConsoStep(lang, n)}.png"),
      contentId = Some(s"${schemaSignalConsoStep(lang, n)}")
    )
  )

  def ConsumerProResponseNotificationAttachement(lang: Locale): Seq[Attachment] =
    defaultAttachments ++ attachmentSeqForWorkflowStepN(4, lang) ++ Seq(
      AttachmentFile(
        s"happy.png",
        environment.getFile(s"/appfiles/happy.png"),
        contentId = Some(s"happy")
      ),
      AttachmentFile(
        s"neutral.png",
        environment.getFile(s"/appfiles/neutral.png"),
        contentId = Some(s"neutral")
      ),
      AttachmentFile(
        s"sad.png",
        environment.getFile(s"/appfiles/sad.png"),
        contentId = Some(s"sad")
      )
    )

  def needWorkflowSeqForWorkflowStepN(n: Int, report: Report): Seq[Attachment] =
    if (report.needWorkflowAttachment()) {
      attachmentSeqForWorkflowStepN(n, report.lang.getOrElse(Locale.FRENCH))
    } else defaultAttachments

  def reportAcknowledgmentAttachement(
      report: Report,
      maybeCompany: Option[Company],
      event: Event,
      files: Seq[ReportFile],
      messagesProvider: MessagesProvider
  ): Seq[Attachment] =
    needWorkflowSeqForWorkflowStepN(2, report) ++
      Seq(
        AttachmentData(
          "Signalement.pdf",
          pdfService.getPdfData(
            views.html.pdfs.report(
              report,
              maybeCompany,
              Seq((event, None)),
              None,
              None,
              None,
              Seq.empty,
              files.map(ReportFileApi.build(_))
            )(
              frontRoute,
              None,
              messagesProvider
            )
          ),
          "application/pdf"
        )
      ).filter(_ => report.isContractualDispute() && report.companyId.isDefined)

}
