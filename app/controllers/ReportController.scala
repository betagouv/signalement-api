package controllers

import java.io.FileInputStream
import java.time.{LocalDate, YearMonth}
import java.util.UUID

import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import javax.inject.Inject
import models.{Report, Statistics}
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.libs.mailer.AttachmentFile
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Environment, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories.{FileRepository, ReportRepository}
import services.{MailerService, S3Service}

import scala.concurrent.{ExecutionContext, Future}

class ReportingController @Inject()(reportingRepository: ReportRepository,
                                    fileRepository: FileRepository,
                                    mailerService: MailerService,
                                    s3Service: S3Service,
                                    configuration: Configuration,
                                    environment: Environment)
                                   (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def createReporting = Action.async(parse.multipartFormData) { implicit request =>

    logger.debug("createReporting")

    ReportingForms.createReportingForm.bindFromRequest(request.body.asFormUrlEncoded).fold(
      formWithErrors => treatFormErrors(formWithErrors),
      form => {
        for {
          reporting <- reportingRepository.create(
            Report(
              UUID.randomUUID(),
              form.category,
              form.subcategory,
              form.precision,
              form.companyName,
              form.companyAddress,
              form.companyPostalCode,
              form.companySiret,
              LocalDate.now(),
              form.anomalyDate,
              form.anomalyTimeSlot,
              form.description,
              form.firstName,
              form.lastName,
              form.email,
              form.contactAgreement,
              None,
              None)
          )
          ticketFileId <- addFile(request.body.file("ticketFile"))
          anomalyFileId <- addFile(request.body.file("anomalyFile"))
          reporting <- reportingRepository.update(reporting.copy(ticketFileId = ticketFileId, anomalyFileId = anomalyFileId))
          mailNotification <- sendReportingNotificationByMail(reporting, request.body.file("ticketFile"), request.body.file("anomalyFile"))
          mailAcknowledgment <- sendReportingAcknowledgmentByMail(reporting, request.body.file("ticketFile"), request.body.file("anomalyFile"))
        } yield {
          Ok(Json.toJson(reporting))
        }
      }
    )
  }

  def upload = Action(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
    val maybeUploadResult =
      request.body.file("reportFile").map {
        case FilePart(key, filename, contentType, multipartUploadResult) =>
          multipartUploadResult
      }

    maybeUploadResult.fold(
      InternalServerError("Echec de l'upload")
    )(uploadResult =>
      Ok(s"Fichier ${uploadResult.key} uploadé")
    )
  }

  private def handleFilePartAwsUploadResult: Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType) =>
      val accumulator = Accumulator(s3Service.upload(configuration.get[String]("play.buckets.report"), filename))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

  def treatFormErrors(formWithErrors: Form[ReportingForms.CreatReportingForm]) = {
    logger.error(s"Error createReporting ${formWithErrors.errors}")
    Future.successful(BadRequest(
      Json.obj("errors" ->
        Json.toJson(formWithErrors.errors.map(error => (error.key, error.message)))
      )
    ))
  }

  def addFile(fileToAdd: Option[MultipartFormData.FilePart[Files.TemporaryFile]]) = {
    logger.debug(s"file ${fileToAdd.map(_.filename)}")
    fileToAdd match {
      case Some(file) => fileRepository.uploadFile(new FileInputStream(file.ref))
      case None => Future(None)
    }
  }

  def sendReportingNotificationByMail(reporting: Report, files: Option[MultipartFormData.FilePart[Files.TemporaryFile]]*) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.reportingNotification(reporting).toString,
      attachments = files.filter(fileOption => fileOption.isDefined).map(file => AttachmentFile(file.get.filename, file.get.ref))
    ))
  }

  def sendReportingAcknowledgmentByMail(reporting: Report, files: Option[MultipartFormData.FilePart[Files.TemporaryFile]]*) = {
    reporting.category match {
      case "Intoxication alimentaire" => Future(())
      case _ =>
        Future(mailerService.sendEmail(
          from = configuration.get[String]("play.mail.from"),
          recipients = reporting.email)(
          subject = "Votre signalement",
          bodyHtml = views.html.mails.reportingAcknowledgment(reporting, configuration.get[String]("play.mail.contactRecipient")).toString,
          attachments = Seq(
            AttachmentFile("logo-marianne.png", environment.getFile("/appfiles/logo-marianne.png"), contentId = Some("logo"))
          )
        ))
    }
  }

  def getStatistics = Action.async { implicit request =>

    for {
      reportsCount <- reportingRepository.count
      reportsPerMonth <- reportingRepository.countPerMonth
    } yield {
      Ok(Json.toJson(
        Statistics(
          reportsCount,
          reportsPerMonth.filter(stat => stat.yearMonth.isAfter(YearMonth.now().minusYears(1)))
        )
      ))
    }
  }
}


object ReportingForms {

  case class CreatReportingForm(
                                 category: String,
                                 subcategory: Option[String],
                                 precision: Option[String],
                                 companyName: String,
                                 companyAddress: String,
                                 companyPostalCode: Option[String],
                                 companySiret: Option[String],
                                 anomalyDate: LocalDate,
                                 anomalyTimeSlot: Option[Int],
                                 description: Option[String],
                                 firstName: String,
                                 lastName: String,
                                 email: String,
                                 contactAgreement: Boolean
                               )

  val createReportingForm = Form(mapping(
    "category" -> nonEmptyText,
    "subcategory" -> optional(text),
    "precision" -> optional(text),
    "companyName" -> nonEmptyText,
    "companyAddress" -> nonEmptyText,
    "companyPostalCode" -> optional(text),
    "companySiret" -> optional(text),
    "anomalyDate" -> localDate("yyyy-MM-dd"),
    "anomalyTimeSlot" -> optional(number),
    "description" -> optional(text),
    "firstName" -> nonEmptyText,
    "lastName" -> nonEmptyText,
    "email" -> email,
    "contactAgreement" -> boolean
  )(CreatReportingForm.apply)(CreatReportingForm.unapply))

}
