package services

import actors.ReportsExtractActor.ReportColumn
import config.SignalConsoConfiguration
import controllers.routes
import models.UserRole.Admin
import models.UserRole.DGCCRF
import models._
import models.engagement.Engagement.EngagementReminderPeriod
import models.report._
import models.report.review.ResponseEvaluation
import spoiwo.model._
import utils.Constants
import utils.Constants.Departments
import utils.DateUtils.frenchFormatDate
import utils.ExcelUtils._

import java.time.ZoneId

object ExcelColumnsService {
  def buildColumns(
      signalConsoConfiguration: SignalConsoConfiguration,
      requestedBy: User,
      zone: ZoneId
  ): List[ReportColumn] = {
    val userRole       = requestedBy.userRole
    val isAgentOrAdmin = UserRole.isAdminOrAgent(userRole)
    List(
      ReportColumn(
        "Date de création",
        (report, _, _, _, _, _) => frenchFormatDate(report.creationDate, zone)
      ),
      ReportColumn(
        "Département",
        (report, _, _, _, _, _) => report.companyAddress.postalCode.flatMap(Departments.fromPostalCode).getOrElse(""),
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Code postal",
        (report, _, _, _, _, _) => report.companyAddress.postalCode.getOrElse(""),
        available = isAgentOrAdmin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Pays",
        (report, _, _, _, _, _) => report.companyAddress.country.map(_.name).getOrElse(""),
        available = isAgentOrAdmin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Siret",
        (report, _, _, _, _, _) => report.companySiret.map(_.value).getOrElse(""),
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Nom de l'entreprise",
        (report, _, _, _, _, _) => report.companyName.getOrElse("")
      ),
      ReportColumn(
        "Adresse de l'entreprise",
        (report, _, _, _, _, _) => report.companyAddress.toString
      ),
      ReportColumn(
        "Email de l'entreprise",
        (_, _, _, _, _, companyAdmins) => companyAdmins.map(_.email).mkString(","),
        available = userRole == Admin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Site web de l'entreprise",
        (report, _, _, _, _, _) => report.websiteURL.websiteURL.map(_.value).getOrElse(""),
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Téléphone de l'entreprise",
        (report, _, _, _, _, _) => report.phone.getOrElse(""),
        available = isAgentOrAdmin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Vendeur (marketplace)",
        (report, _, _, _, _, _) => report.vendor.getOrElse(""),
        available = isAgentOrAdmin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Train",
        (report, _, _, _, _, _) =>
          report.train.map(t => s"${t.train} ${t.nightTrain.getOrElse("")} ${t.ter.getOrElse("")}").getOrElse(""),
        available = isAgentOrAdmin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Catégorie",
        (report, _, _, _, _, _) => ReportCategory.displayValue(report.category)
      ),
      ReportColumn(
        "Sous-catégories",
        (report, _, _, _, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Détails",
        (report, _, _, _, _, _) =>
          report.details.map(d => s"${d.label} ${d.value}").mkString("\n").replace("&#160;", " "),
        column = Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle)
      ),
      ReportColumn(
        "Pièces jointes",
        (_, files, _, _, _, _) =>
          files
            .filter(file => file.origin == ReportFileOrigin.Consumer)
            .map(file =>
              s"${signalConsoConfiguration.apiURL.toString}${routes.ReportFileController
                  .downloadReportFile(file.id, file.filename)
                  .url}"
            )
            .mkString("\n"),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Influenceur ou influenceuse",
        (report, _, _, _, _, _) => report.influencer.map(_.name).getOrElse("")
      ),
      ReportColumn(
        "Plateforme (réseau social)",
        (report, _, _, _, _, _) =>
          report.influencer
            .flatMap(_.socialNetwork)
            .map(_.entryName)
            .orElse(report.influencer.flatMap(_.otherSocialNetwork))
            .getOrElse("")
      ),
      ReportColumn(
        "Statut",
        (report, _, _, _, _, _) => ReportStatus.translate(report.status, userRole),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Répondant",
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(_.user)
            .map(u => s"${u.firstName} ${u.lastName}")
            .getOrElse("")
      ),
      ReportColumn(
        "Date de Réponse du professionnel",
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .map(c => frenchFormatDate(c.event.creationDate, zone))
            .getOrElse("")
      ),
      ReportColumn(
        "Date limite de réalisation de la promesse",
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .map(c => frenchFormatDate(c.event.creationDate.plusDays(EngagementReminderPeriod.toLong), zone))
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse du professionnel",
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(_.event.details.validate[ExistingReportResponse].asOpt)
            .map(response => ReportResponseType.translate(response.responseType))
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse du professionnel (détails)",
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(_.event.details.validate[ExistingReportResponse].asOpt)
            .flatMap(response => ExistingReportResponse.translateResponseDetails(response))
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse au consommateur",
        (report, _, events, _, _, _) =>
          Some(report.status)
            .filter(
              List(
                ReportStatus.PromesseAction,
                ReportStatus.MalAttribue,
                ReportStatus.Infonde
              ) contains _
            )
            .flatMap(_ =>
              events
                .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
                .flatMap(_.event.details.asOpt[ExistingReportResponse].map(_.consumerDetails))
            )
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse à la DGCCRF",
        (report, _, events, _, _, _) =>
          Some(report.status)
            .filter(
              List(
                ReportStatus.PromesseAction,
                ReportStatus.MalAttribue,
                ReportStatus.Infonde
              ) contains _
            )
            .flatMap(_ =>
              events
                .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
                .flatMap(_.event.details.asOpt[ExistingReportResponse].flatMap(_.dgccrfDetails))
            )
            .getOrElse("")
      ),
      ReportColumn(
        "Avis initial du consommateur",
        (_, _, _, review, _, _) => review.map(r => ResponseEvaluation.translate(r.evaluation)).getOrElse("")
      ),
      ReportColumn(
        "Précisions de l'avis initial du consommateur",
        (_, _, _, review, _, _) => review.flatMap(_.details).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Date de l'avis initial du consommateur",
        (_, _, _, review, _, _) => review.map(r => frenchFormatDate(r.creationDate, zone)).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Avis ultérieur du consommateur",
        (_, _, _, _, engagementReview, _) =>
          engagementReview.map(r => ResponseEvaluation.translate(r.evaluation)).getOrElse("")
      ),
      ReportColumn(
        "Précisions de l'avis ultérieur du consommateur",
        (_, _, _, _, engagementReview, _) => engagementReview.flatMap(_.details).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Date de l'avis ultérieur du consommateur",
        (_, _, _, _, engagementReview, _) =>
          engagementReview.map(r => frenchFormatDate(r.creationDate, zone)).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Identifiant",
        (report, _, _, _, _, _) => report.id.toString,
        available = isAgentOrAdmin,
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Prénom",
        (report, _, _, _, _, _) => report.firstName,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Nom",
        (report, _, _, _, _, _) => report.lastName,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Email",
        (report, _, _, _, _, _) => report.email.value,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Téléphone",
        (report, _, _, _, _, _) => report.consumerPhone.filter(_ => report.contactAgreement).getOrElse("")
      ),
      ReportColumn(
        "Numéro de référence dossier",
        (report, _, _, _, _, _) => report.consumerReferenceNumber.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Anonyme",
        (report, _, _, _, _, _) => if (report.contactAgreement) "Oui" else "Non",
        column = centerAlignmentColumn
      ),
      ReportColumn(
        "Actions DGCCRF",
        (_, _, events, _, _, _) =>
          events
            .filter(_.event.eventType == Constants.EventType.DGCCRF)
            .map(eventWithUser =>
              s"Le ${frenchFormatDate(eventWithUser.event.creationDate, zone)} : ${eventWithUser.event.action.value} - ${eventWithUser.event.getDescription}"
            )
            .mkString("\n"),
        available = userRole == DGCCRF
      ),
      ReportColumn(
        "Contrôle effectué",
        (
            _,
            _,
            events,
            _,
            _,
            _
        ) => if (events.exists(_.event.action == Constants.ActionEvent.CONTROL)) "Oui" else "Non",
        available = userRole == DGCCRF,
        column = centerAlignmentColumn
      )
    )
  }

}
