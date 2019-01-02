package controllers

import java.io.FileInputStream
import java.time.LocalDate
import java.util.UUID

import javax.inject.Inject
import models.Reporting
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.libs.mailer.AttachmentFile
import play.api.mvc.MultipartFormData
import play.api.{Configuration, Environment, Logger}
import repositories.{FileRepository, ReportingRepository}
import services.MailerService

import scala.concurrent.{ExecutionContext, Future}

class ReportingController @Inject()(reportingRepository: ReportingRepository,
                                    fileRepository: FileRepository,
                                    mailerService: MailerService,
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
            Reporting(
              UUID.randomUUID(),
              form.companyType,
              form.anomalyCategory,
              form.anomalyPrecision,
              form.companyName,
              form.companyAddress,
              form.companyPostalCode,
              form.companySiret,
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

  def sendReportingNotificationByMail(reporting: Reporting, files: Option[MultipartFormData.FilePart[Files.TemporaryFile]]*) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.reportingNotification(reporting).toString,
      attachments = files.filter(fileOption => fileOption.isDefined).map(file => AttachmentFile(file.get.filename, file.get.ref))
    ))
  }

  def sendReportingAcknowledgmentByMail(reporting: Reporting, files: Option[MultipartFormData.FilePart[Files.TemporaryFile]]*) = {
    reporting.anomalyCategory match {
      case "Intoxication alimentaire" => Future(())
      case _ =>
        Future(mailerService.sendEmail(
          from = configuration.get[String]("play.mail.from"),
          recipients = reporting.email)(
          subject = "Votre signalement",
          bodyHtml = views.html.mails.reportingAcknowledgment(reporting).toString,
          attachments = Seq(
            AttachmentFile("logo-marianne.png", environment.getFile("/appfiles/logo-marianne.png"), contentId = Some("logo"))
          )
        ))
    }
  }
}


object ReportingForms {

  case class CreatReportingForm(
                              companyType: String,
                              anomalyCategory: String,
                              anomalyPrecision: Option[String],
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
    "companyType" -> nonEmptyText,
    "anomalyCategory" -> nonEmptyText,
    "anomalyPrecision" -> optional(text),
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
