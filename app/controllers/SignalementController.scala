package controllers

import java.io.FileInputStream
import java.util.UUID

import javax.inject.Inject
import models.Signalement
import play.api.Logger
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData
import repositories.{FileRepository, SignalementRepository}

import scala.concurrent.{ExecutionContext, Future}

class SignalementController @Inject()(signalementRepository: SignalementRepository,
                                      fileRepository: FileRepository)
                                     (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def createSignalement = Action.async(parse.multipartFormData) { implicit request =>

    logger.debug("createSignalement")

    SignalementForms.createSignalementForm.bindFromRequest(request.body.asFormUrlEncoded).fold(
      formWithErrors => treatFormErrors(formWithErrors),
      form => {
        for {
          signalement <- signalementRepository.create(
            Signalement(
              UUID.randomUUID(),
              form.typeEtablissement,
              form.categorieAnomalie,
              form.precisionAnomalie,
              form.nomEtablissement,
              form.adresseEtablissement,
              form.description,
              form.prenom,
              form.nom,
              form.email,
              None)
            )
          oid <- addFile(request.body.file("file"))
          signalement <- signalementRepository.update(signalement.copy(photoOID = oid))
        } yield {
          Ok(Json.toJson(signalement))
        }
      }
    )
  }

  def treatFormErrors(formWithErrors: Form[SignalementForms.CreateSignalementForm]) = {
    logger.error(s"Error createSignalement ${formWithErrors.errors}")
    Future.successful(BadRequest(
      Json.obj("errors" ->
        Json.toJson(formWithErrors.errors.map(error => (error.key, error.message)))
      )
    ))
  }

  def addFile(fileToAdd: Option[MultipartFormData.FilePart[Files.TemporaryFile]]) = {
    logger.debug(s"file $fileToAdd")
    fileToAdd match {
      case Some(file) => fileRepository.uploadFile(new FileInputStream(file.ref))
      case None => Future(None)
    }
  }


}



object SignalementForms {

  case class CreateSignalementForm(
                              typeEtablissement: String,
                              categorieAnomalie: String,
                              precisionAnomalie: String,
                              nomEtablissement: String,
                              adresseEtablissement: String,
                              description: Option[String],
                              prenom: String,
                              nom: String,
                              email: String
                            )

  val createSignalementForm = Form(mapping(
    "typeEtablissement" -> nonEmptyText,
    "categorieAnomalie" -> nonEmptyText,
    "precisionAnomalie" -> nonEmptyText,
    "nomEtablissement" -> nonEmptyText,
    "adresseEtablissement" -> nonEmptyText,
    "description" -> optional(text),
    "prenom" -> nonEmptyText,
    "nom" -> nonEmptyText,
    "email" -> email
  )(CreateSignalementForm.apply)(CreateSignalementForm.unapply))

}
