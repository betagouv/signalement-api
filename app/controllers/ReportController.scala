package controllers

import java.time.{LocalDateTime, YearMonth}
import java.util.UUID

import akka.stream.alpakka.s3.scaladsl.MultipartUploadResult
import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import models.{File, Report, Statistics}
import play.api.libs.json.{JsError, Json}
import play.api.libs.mailer.AttachmentFile
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.{Configuration, Environment, Logger}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import repositories.{ReportFilter, ReportRepository}
import services.{MailerService, S3Service}
import utils.silhouette.AuthEnv

import scala.concurrent.{ExecutionContext, Future}

class ReportController @Inject()(reportRepository: ReportRepository,
                                 mailerService: MailerService,
                                 s3Service: S3Service,
                                 val silhouette: Silhouette[AuthEnv],
                                 configuration: Configuration,
                                 environment: Environment)
                                (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  val BucketName = configuration.get[String]("play.buckets.report")


  val departmentsAuthorized = List(
    "01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74", // AURA
    "18", "28", "36", "37", "41", "45" // CVDL
  )

  def determineStatusPro(report: Report): Option[String] = {

    if (departmentsAuthorized.contains(report.companyPostalCode.get.slice(0, 2))) Some("A-CONTACTER") else Some("HORS-PERIMETRE")
  }

  def createReport = UserAwareAction.async(parse.json) { implicit request =>

    logger.debug("createReport")

    request.body.validate[Report].fold(
      errors => Future.successful(BadRequest(JsError.toJson(errors))),
      report => {
        for {
          report <- reportRepository.create(
            report.copy(
              id = Some(UUID.randomUUID()),
              creationDate = Some(LocalDateTime.now()),
              statusPro = determineStatusPro(report)
            )
          )
          attachFilesToReport <- reportRepository.attachFilesToReport(report.files.map(_.id), report.id.get)
          files <- reportRepository.retrieveReportFiles(report.id.get)
          mailNotification <- sendReportNotificationByMail(report, files)
          mailAcknowledgment <- sendReportAcknowledgmentByMail(report, files)
        } yield {
          Ok(Json.toJson(report))
        }
      }
    )
  }

  def uploadReportFile = UserAwareAction.async(parse.multipartFormData(handleFilePartAwsUploadResult)) { request =>
    val maybeUploadResult =
      request.body.file("reportFile").map {
        case FilePart(key, filename, contentType, multipartUploadResult) =>
          (multipartUploadResult, filename)
      }

    maybeUploadResult.fold(Future(InternalServerError("Echec de l'upload"))) {
      maybeUploadResult =>
        reportRepository.createFile(
          File(UUID.fromString(maybeUploadResult._1.key), None, LocalDateTime.now(), maybeUploadResult._2)
        ).map(file => Ok(Json.toJson(file)))
    }
  }

  private def handleFilePartAwsUploadResult: Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType) =>
      val accumulator = Accumulator(s3Service.upload(BucketName, UUID.randomUUID.toString))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

  def sendReportNotificationByMail(report: Report, files: List[File])(implicit request: play.api.mvc.Request[Any]) = {
    Future(mailerService.sendEmail(
      from = configuration.get[String]("play.mail.from"),
      recipients = configuration.get[String]("play.mail.contactRecipient"))(
      subject = "Nouveau signalement",
      bodyHtml = views.html.mails.reportNotification(report, files).toString
    ))
  }

  def sendReportAcknowledgmentByMail(report: Report, files: List[File]) = {
    report.category match {
      case "Intoxication alimentaire" => Future(())
      case _ =>
        Future(mailerService.sendEmail(
          from = configuration.get[String]("play.mail.from"),
          recipients = report.email)(
          subject = "Votre signalement",
          bodyHtml = views.html.mails.reportAcknowledgment(report, configuration.get[String]("play.mail.contactRecipient"), files).toString,
          attachments = Seq(
            AttachmentFile("logo-marianne.png", environment.getFile("/appfiles/logo-marianne.png"), contentId = Some("logo"))
          )
        ))
    }
  }

  def downloadReportFile(uuid: String, filename: String) = UserAwareAction.async { implicit request =>
    reportRepository.getFile(UUID.fromString(uuid)).flatMap(_ match {
      case Some(file) if file.filename == filename =>
        s3Service.download(BucketName, uuid).flatMap(
          file => {
            val dest: Array[Byte] = new Array[Byte](file.asByteBuffer.capacity())
            file.asByteBuffer.get(dest)
            Future(Ok(dest))
          }
        )
      case _ => Future(NotFound)
    })
  }

  def deleteReportFile(uuid: String, filename: String) = UserAwareAction.async { implicit request =>
    reportRepository.getFile(UUID.fromString(uuid)).flatMap(_ match {
      case Some(file) if file.filename == filename =>
        for {
          repositoryDelete <- reportRepository.deleteFile(UUID.fromString(uuid))
          s3Delete <- s3Service.delete(BucketName, uuid)
        } yield Ok
      case _ => Future(NotFound)
    })
  }

  def getStatistics = UserAwareAction.async { implicit request =>

    for {
      reportsCount <- reportRepository.count
      reportsPerMonth <- reportRepository.countPerMonth
    } yield {
      Ok(Json.toJson(
        Statistics(
          reportsCount,
          reportsPerMonth.filter(stat => stat.yearMonth.isAfter(YearMonth.now().minusYears(1)))
        )
      ))
    }
  }

  // private def getSortList(input: Option[String]): List[String] = {

  //   val DIRECTIONS = List("asc", "desc")

  //   var res = new ListBuffer[String]()
  
  //   if (input.isDefined) {
  //     var fields = input.get.split(',').toList

  //     for (elt <- fields) {
  //       val parts = elt.split('.').toList

  //       if (parts.length > 0) {
          
  //         val index = reportRepository.getFieldsClassName.map(_.toLowerCase).indexOf(parts(0).toLowerCase)

  //         if (index > 0) {
  //           if (parts.length > 1) {
  //             if (DIRECTIONS.contains(parts(1))) {
  //               res += reportRepository.getFieldsClassName(index) + '.' + parts(1)
  //             } else {
  //               res += reportRepository.getFieldsClassName(index)
  //             }
  //           } else {
  //             res += reportRepository.getFieldsClassName(index)
  //           }
  //         }
          
  //       }
  //     }
  //   }

  //   //res.toList.map(println)

  //   return res.toList
  // }

  def getReport(uuid: String) = SecuredAction.async { implicit request =>

    reportRepository.getReport(UUID.fromString(uuid)).flatMap(report => {

      Future(Ok(Json.toJson(report)))
    })

  }
 
  def getReports(
    offset: Option[Long], 
    limit: Option[Int], 
    sort: Option[String], 
    codePostal: Option[String],
    email: Option[String],
    siret: Option[String],
    entreprise: Option[String]
  ) = SecuredAction.async { implicit request =>

    // valeurs par défaut
    val LIMIT_DEFAULT = 25
    val LIMIT_MAX = 250

    // normalisation des entrées
    var offsetNormalized: Long = offset.map(Math.max(_, 0)).getOrElse(0)
    var limitNormalized = limit.map(Math.max(_, 0)).map(Math.min(_, LIMIT_MAX)).getOrElse(LIMIT_DEFAULT)

    val filter = ReportFilter(codePostal = codePostal, email = email, siret = siret, entreprise = entreprise)
    
    reportRepository.getReports(offsetNormalized, limitNormalized, filter).flatMap( reports => {

      Future(Ok(Json.toJson(reports)))
    })

  }

  def getEvents(uuidReport: String, eventType: Option[String]) = SecuredAction.async { implicit request =>

    println(">>> Dans getEvents")

    // TODO tester si type appartient à Constants.EventPro.EventTypeValues

    reportRepository.getEvents(UUID.fromString(uuidReport), eventType).flatMap( events => {

      Future(Ok(Json.toJson(events)))

  })

  }
}
