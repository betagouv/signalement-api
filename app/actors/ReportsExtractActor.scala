package actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import spoiwo.model._
import spoiwo.model.enums.CellFill
import spoiwo.model.enums.CellHorizontalAlignment
import spoiwo.model.enums.CellStyleInheritance
import spoiwo.model.enums.CellVerticalAlignment
import spoiwo.natures.xlsx.Model2XlsxConversions._
import config.SignalConsoConfiguration
import controllers.routes
import models._
import models.company.AccessLevel
import models.event.Event
import models.report.Report
import models.report.ReportCategory
import models.report.ReportFile
import models.report.ReportFileOrigin
import models.report.ReportFilter
import models.report.ReportResponse
import models.report.ReportResponseType
import models.report.ReportStatus
import models.report.review.ResponseConsumerReview
import models.report.review.ResponseEvaluation
import orchestrators.ReportConsumerReviewOrchestrator
import orchestrators.ReportOrchestrator
import play.api.Logger
import repositories.asyncfiles.AsyncFileRepositoryInterface
import repositories.companyaccess.CompanyAccessRepositoryInterface
import repositories.event.EventRepositoryInterface
import repositories.reportfile.ReportFileRepositoryInterface
import services.S3ServiceInterface
import utils.Constants
import utils.Constants.Departments
import utils.DateUtils.frenchFormatDate
import utils.DateUtils.frenchFormatDateAndTime

import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success
import java.time.ZoneId
import java.time.OffsetDateTime

object ReportsExtractActor {
  sealed trait ReportsExtractCommand
  case class ExtractRequest(fileId: UUID, requestedBy: User, filters: ReportFilter, zone: ZoneId)
      extends ReportsExtractCommand
  case class ExtractRequestSuccess(fileId: UUID, requestedBy: User) extends ReportsExtractCommand
  case object ExtractRequestFailure                                 extends ReportsExtractCommand

  val logger: Logger = Logger(this.getClass)

  def create(
      reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
      reportFileRepository: ReportFileRepositoryInterface,
      companyAccessRepository: CompanyAccessRepositoryInterface,
      reportOrchestrator: ReportOrchestrator,
      eventRepository: EventRepositoryInterface,
      asyncFileRepository: AsyncFileRepositoryInterface,
      s3Service: S3ServiceInterface,
      signalConsoConfiguration: SignalConsoConfiguration
  )(implicit mat: Materializer): Behavior[ReportsExtractCommand] =
    Behaviors.setup { context =>
      import context.executionContext

      Behaviors.receiveMessage[ReportsExtractCommand] {
        case ExtractRequest(fileId: UUID, requestedBy: User, filters: ReportFilter, zone: ZoneId) =>
          val result = for {
            // FIXME: We might want to move the random name generation
            // in a common place if we want to reuse it for other async files
            tmpPath <- genTmpFile(
              reportOrchestrator,
              signalConsoConfiguration,
              reportFileRepository,
              eventRepository,
              reportConsumerReviewOrchestrator,
              companyAccessRepository,
              requestedBy,
              filters,
              zone
            )
            remotePath <- saveRemotely(s3Service, tmpPath, tmpPath.getFileName.toString)
            _          <- asyncFileRepository.update(fileId, tmpPath.getFileName.toString, remotePath)
          } yield ExtractRequestSuccess(fileId, requestedBy)

          context.pipeToSelf(result) {
            case Success(success) => success
            case Failure(_)       => ExtractRequestFailure
          }
          Behaviors.same

        case ExtractRequestSuccess(fileId: UUID, requestedBy: User) =>
          logger.debug(s"Built report for User ${requestedBy.id} — async file ${fileId}")
          Behaviors.same

        case ExtractRequestFailure =>
          logger.info(s"Extract failed")
          Behaviors.same
      }
    }

  // Common layout variables
  private val headerStyle = CellStyle(
    fillPattern = CellFill.Solid,
    fillForegroundColor = Color.Gainsborough,
    font = Font(bold = true),
    horizontalAlignment = CellHorizontalAlignment.Center
  )
  private val centerAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Center,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  private val leftAlignmentStyle = CellStyle(
    horizontalAlignment = CellHorizontalAlignment.Left,
    verticalAlignment = CellVerticalAlignment.Center,
    wrapText = true
  )
  private val leftAlignmentColumn   = Column(autoSized = true, style = leftAlignmentStyle)
  private val centerAlignmentColumn = Column(autoSized = true, style = centerAlignmentStyle)
  private val MaxCharInSingleCell   = 10000

  // Columns definition
  case class ReportColumn(
      name: String,
      column: Column,
      extract: (Report, List[ReportFile], List[Event], Option[ResponseConsumerReview], List[User]) => String,
      available: Boolean = true
  ) {
    def extractStringValue(
        report: Report,
        reportFiles: List[ReportFile],
        events: List[Event],
        consumerReview: Option[ResponseConsumerReview],
        users: List[User]
    ): String = extract(report, reportFiles, events, consumerReview, users).take(MaxCharInSingleCell)
  }

  private def buildColumns(
      signalConsoConfiguration: SignalConsoConfiguration,
      requestedBy: User,
      zone: ZoneId
  ): List[ReportColumn] = {
    List(
      ReportColumn(
        "Date de création",
        centerAlignmentColumn,
        (report, _, _, _, _) => frenchFormatDate(report.creationDate, zone)
      ),
      ReportColumn(
        "Département",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.companyAddress.postalCode.flatMap(Departments.fromPostalCode).getOrElse("")
      ),
      ReportColumn(
        "Code postal",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.companyAddress.postalCode.getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Pays",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.companyAddress.country.map(_.name).getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Siret",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.companySiret.map(_.value).getOrElse("")
      ),
      ReportColumn(
        "Nom de l'entreprise",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.companyName.getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Adresse de l'entreprise",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.companyAddress.toString,
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Email de l'entreprise",
        centerAlignmentColumn,
        (_, _, _, _, companyAdmins) => companyAdmins.map(_.email).mkString(","),
        available = requestedBy.userRole == UserRole.Admin
      ),
      ReportColumn(
        "Site web de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.websiteURL.websiteURL.map(_.value).getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Téléphone de l'entreprise",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.phone.getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Vendeur (marketplace)",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.vendor.getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Catégorie",
        leftAlignmentColumn,
        (report, _, _, _, _) => ReportCategory.displayValue(report.category)
      ),
      ReportColumn(
        "Sous-catégories",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.subcategories.filter(s => s != null).mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Détails",
        Column(width = new Width(100, WidthUnit.Character), style = leftAlignmentStyle),
        (report, _, _, _, _) => report.details.map(d => s"${d.label} ${d.value}").mkString("\n").replace("&#160;", " ")
      ),
      ReportColumn(
        "Pièces jointes",
        leftAlignmentColumn,
        (_, files, _, _, _) =>
          files
            .filter(file => file.origin == ReportFileOrigin.Consumer)
            .map(file =>
              s"${signalConsoConfiguration.apiURL.toString}${routes.ReportFileController
                  .downloadReportFile(file.id, file.filename)
                  .url}"
            )
            .mkString("\n"),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Influenceur ou influenceuse",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.influencer.map(_.name).getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Plateforme (réseau social)",
        leftAlignmentColumn,
        (report, _, _, _, _) =>
          report.influencer
            .flatMap(_.socialNetwork)
            .map(_.entryName)
            .orElse(report.influencer.flatMap(_.otherSocialNetwork))
            .getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Statut",
        leftAlignmentColumn,
        (report, _, _, _, _) => ReportStatus.translate(report.status, requestedBy.userRole),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Réponse du professionnel",
        leftAlignmentColumn,
        (_, _, events, _, _) =>
          events
            .find(event => event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
            .flatMap(e => e.details.validate[ReportResponse].asOpt)
            .map(response => ReportResponseType.translate(response.responseType))
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse au consommateur",
        leftAlignmentColumn,
        (report, _, events, _, _) =>
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
                .find(event => event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
                .map(e => e.details.validate[ReportResponse].get.consumerDetails)
            )
            .getOrElse("")
      ),
      ReportColumn(
        "Réponse à la DGCCRF",
        leftAlignmentColumn,
        (report, _, events, _, _) =>
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
                .find(event => event.action == Constants.ActionEvent.REPORT_PRO_RESPONSE)
                .flatMap(e => e.details.validate[ReportResponse].get.dgccrfDetails)
            )
            .getOrElse("")
      ),
      ReportColumn(
        "Évaluation du consommateur",
        leftAlignmentColumn,
        (_, _, _, review, _) => review.map(r => ResponseEvaluation.translate(r.evaluation)).getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Réponse du consommateur",
        leftAlignmentColumn,
        (_, _, _, review, _) => review.flatMap(_.details).getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Date de l'évaluation du consommateur",
        leftAlignmentColumn,
        (_, _, _, review, _) => review.map(r => frenchFormatDate(r.creationDate, zone)).getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Identifiant",
        centerAlignmentColumn,
        (report, _, _, _, _) => report.id.toString,
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Prénom",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.firstName,
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Nom",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.lastName,
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Email",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.email.value,
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Téléphone",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.consumerPhone.getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Numéro de référence dossier",
        leftAlignmentColumn,
        (report, _, _, _, _) => report.consumerReferenceNumber.getOrElse(""),
        available = List(UserRole.DGCCRF, UserRole.DGAL, UserRole.Admin) contains requestedBy.userRole
      ),
      ReportColumn(
        "Accord pour contact",
        centerAlignmentColumn,
        (report, _, _, _, _) => if (report.contactAgreement) "Oui" else "Non"
      ),
      ReportColumn(
        "Actions DGCCRF",
        leftAlignmentColumn,
        (_, _, events, _, _) =>
          events
            .filter(event => event.eventType == Constants.EventType.DGCCRF)
            .map(event =>
              s"Le ${frenchFormatDate(event.creationDate, zone)} : ${event.action.value} - ${event.getDescription}"
            )
            .mkString("\n"),
        available = requestedBy.userRole == UserRole.DGCCRF
      ),
      ReportColumn(
        "Contrôle effectué",
        centerAlignmentColumn,
        (
            _,
            _,
            events,
            _,
            _
        ) => if (events.exists(event => event.action == Constants.ActionEvent.CONTROL)) "Oui" else "Non",
        available = requestedBy.userRole == UserRole.DGCCRF
      )
    ).filter(_.available)
  }

  private def genTmpFile(
      reportOrchestrator: ReportOrchestrator,
      signalConsoConfiguration: SignalConsoConfiguration,
      reportFileRepository: ReportFileRepositoryInterface,
      eventRepository: EventRepositoryInterface,
      reportConsumerReviewOrchestrator: ReportConsumerReviewOrchestrator,
      companyAccessRepository: CompanyAccessRepositoryInterface,
      requestedBy: User,
      filters: ReportFilter,
      zone: ZoneId
  )(implicit ec: ExecutionContext): Future[Path] = {
    val reportColumns = buildColumns(signalConsoConfiguration, requestedBy, zone)
    for {
      paginatedReports <- reportOrchestrator
        .getReportsForUser(
          requestedBy,
          filter = filters,
          offset = Some(0),
          limit = Some(signalConsoConfiguration.reportsExportLimitMax)
        )
        .map(_.entities.map(_.report))
      reportFilesMap     <- reportFileRepository.prefetchReportsFiles(paginatedReports.map(_.id))
      reportEventsMap    <- eventRepository.fetchEventsOfReports(paginatedReports)
      consumerReviewsMap <- reportConsumerReviewOrchestrator.find(paginatedReports.map(_.id))
      companyAdminsMap <- companyAccessRepository.fetchUsersByCompanyIds(
        paginatedReports.flatMap(_.companyId),
        Seq(AccessLevel.ADMIN)
      )
    } yield {
      val targetFilename = s"signalements-${Random.alphanumeric.take(12).mkString}.xlsx"
      val reportsSheet = Sheet(name = "Signalements")
        .withRows(
          Row(style = headerStyle).withCellValues(reportColumns.map(_.name)) ::
            paginatedReports.map(report =>
              Row().withCells(
                reportColumns
                  .map(
                    _.extractStringValue(
                      report,
                      reportFilesMap.getOrElse(report.id, Nil),
                      reportEventsMap.getOrElse(report.id, Nil),
                      consumerReviewsMap.getOrElse(report.id, None),
                      report.companyId.flatMap(companyAdminsMap.get).getOrElse(Nil)
                    )
                  )
                  .map(StringCell(_, None, None, CellStyleInheritance.CellThenRowThenColumnThenSheet))
              )
            )
        )
        .withColumns(reportColumns.map(_.column))

      val filtersSheet = Sheet(name = "Filtres")
        .withRows(
          List(
            Some(
              Row().withCellValues(
                "Date de l'export",
                frenchFormatDateAndTime(OffsetDateTime.now(), zone)
              )
            ),
            Some(filters.departments)
              .filter(_.nonEmpty)
              .map(departments => Row().withCellValues("Départment(s)", departments.mkString(","))),
            (filters.start, filters.end) match {
              case (Some(startDate), Some(endDate)) =>
                Some(
                  Row().withCellValues(
                    "Période",
                    s"Du ${frenchFormatDate(startDate, zone)} au ${frenchFormatDate(endDate, zone)}"
                  )
                )
              case (Some(startDate), _) =>
                Some(Row().withCellValues("Période", s"Depuis le ${frenchFormatDate(startDate, zone)}"))
              case (_, Some(endDate)) =>
                Some(Row().withCellValues("Période", s"Jusqu'au ${frenchFormatDate(endDate, zone)}"))
              case _ => None
            },
            Some(Row().withCellValues("Siret", filters.siretSirenList.mkString(","))),
            filters.websiteURL.map(websiteURL => Row().withCellValues("Site internet", websiteURL)),
            filters.phone.map(phone => Row().withCellValues("Numéro de téléphone", phone)),
            Some(filters.status)
              .filter(_.nonEmpty)
              .map(status =>
                Row()
                  .withCellValues("Statut", status.map(ReportStatus.translate(_, requestedBy.userRole)).mkString(","))
              ),
            filters.category.map(category => Row().withCellValues("Catégorie", category)),
            filters.details.map(details => Row().withCellValues("Mots clés", details))
          ).flatten
        )
        .withColumns(
          Column(autoSized = true, style = headerStyle),
          leftAlignmentColumn
        )

      val localPath = Paths.get(signalConsoConfiguration.tmpDirectory, targetFilename)
      Workbook(reportsSheet, filtersSheet).saveAsXlsx(localPath.toString)
      logger.debug(s"Generated extract locally: ${localPath}")
      localPath
    }
  }

  private def saveRemotely(s3Service: S3ServiceInterface, localPath: Path, remoteName: String)(implicit
      ec: ExecutionContext,
      mat: Materializer
  ): Future[String] = {
    val remotePath = s"extracts/$remoteName"
    s3Service.upload(remotePath).runWith(FileIO.fromPath(localPath)).map(_ => remotePath)
  }
}
