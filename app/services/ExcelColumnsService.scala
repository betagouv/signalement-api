package services

import actors.ReportsExtractActor.ReportColumn
import config.SignalConsoConfiguration
import controllers.routes
import models.UserRole.Admin
import models.UserRole.DGCCRF
import models._
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
    val isAgentOrAdmin = userRole.isAgentOrAdmin

    List(
      ReportColumn(
        "Date de création",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => frenchFormatDate(report.creationDate, zone)
      ),
      ReportColumn(
        "Département",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.companyAddress.postalCode.flatMap(Departments.fromPostalCode).getOrElse("")
      ),
      ReportColumn(
        "Code postal",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.companyAddress.postalCode.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Pays",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.companyAddress.country.map(_.name).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Siret",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.companySiret.map(_.value).getOrElse("")
      ),
      ReportColumn(
        "Nom de l'entreprise",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.companyName.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Adresse de l'entreprise",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.companyAddress.toString,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Email de l'entreprise",
        centerAlignmentColumn,
        (_, _, _, _, _, companyAdmins) => companyAdmins.map(_.email).mkString(","),
        available = userRole == Admin
      ),
      ReportColumn(
        "Site web de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.websiteURL.websiteURL.map(_.value).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Téléphone de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.phone.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Vendeur (marketplace)",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.vendor.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Catégorie",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => ReportCategory.displayValue(report.category)
      ),
      ReportColumn(
        "Sous-catégories",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Détails",
        Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle),
        (report, _, _, _, _, _) =>
          report.details.map(d => s"${d.label} ${d.value}").mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Pièces jointes",
        leftAlignmentColumn,
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
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.influencer.map(_.name).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Plateforme (réseau social)",
        leftAlignmentColumn,
        (report, _, _, _, _, _) =>
          report.influencer
            .flatMap(_.socialNetwork)
            .map(_.entryName)
            .orElse(report.influencer.flatMap(_.otherSocialNetwork))
            .getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Statut",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => ReportStatus.translate(report.status, userRole),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Répondant",
        leftAlignmentColumn,
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(_.user)
            .map(u => s"${u.firstName} ${u.lastName}")
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse du professionnel",
        leftAlignmentColumn,
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(_.event.details.validate[ExistingReportResponse].asOpt)
            .map(response => ReportResponseType.translate(response.responseType))
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse du professionnel (détails)",
        leftAlignmentColumn,
        (_, _, events, _, _, _) =>
          events
            .find(_.event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(_.event.details.validate[ExistingReportResponse].asOpt)
            .flatMap(response => ExistingReportResponse.translateResponseDetails(response))
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse au consommateur",
        leftAlignmentColumn,
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
        leftAlignmentColumn,
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
        leftAlignmentColumn,
        (_, _, _, review, _, _) => review.map(r => ResponseEvaluation.translate(r.evaluation)).getOrElse("")
      ),
      ReportColumn(
        "Précisions de l'avis initial du consommateur",
        leftAlignmentColumn,
        (_, _, _, review, _, _) => review.flatMap(_.details).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Date de l'avis initial du consommateur",
        leftAlignmentColumn,
        (_, _, _, review, _, _) => review.map(r => frenchFormatDate(r.creationDate, zone)).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Avis ultérieur du consommateur",
        leftAlignmentColumn,
        (_, _, _, _, engagementReview, _) =>
          engagementReview.map(r => ResponseEvaluation.translate(r.evaluation)).getOrElse("")
      ),
      ReportColumn(
        "Précisions de l'avis ultérieur du consommateur",
        leftAlignmentColumn,
        (_, _, _, _, engagementReview, _) => engagementReview.flatMap(_.details).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Date de l'avis ultérieur du consommateur",
        leftAlignmentColumn,
        (_, _, _, _, engagementReview, _) =>
          engagementReview.map(r => frenchFormatDate(r.creationDate, zone)).getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Identifiant",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => report.id.toString,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Prénom",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.firstName,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Nom",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.lastName,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Email",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.email.value,
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Téléphone",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.consumerPhone.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Numéro de référence dossier",
        leftAlignmentColumn,
        (report, _, _, _, _, _) => report.consumerReferenceNumber.getOrElse(""),
        available = isAgentOrAdmin
      ),
      ReportColumn(
        "Accord pour contact",
        centerAlignmentColumn,
        (report, _, _, _, _, _) => if (report.contactAgreement) "Oui" else "Non"
      ),
      ReportColumn(
        "Actions DGCCRF",
        leftAlignmentColumn,
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
        centerAlignmentColumn,
        (
            _,
            _,
            events,
            _,
            _,
            _
        ) => if (events.exists(_.event.action == Constants.ActionEvent.CONTROL)) "Oui" else "Non",
        available = userRole == DGCCRF
      )
    ).filter(_.available)
  }

}
