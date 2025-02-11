package controllers

import authentication.Authenticator
import models.User
import play.api.mvc.ControllerComponents
import services.SiretExtractorService
import play.api.libs.json.Json
import repositories.siretextraction.SiretExtractionRepositoryInterface

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class SiretExtractorController(
    siretExtractionRepository: SiretExtractionRepositoryInterface,
    siretExtractorService: SiretExtractorService,
    authenticator: Authenticator[User],
    controllerComponents: ControllerComponents
)(implicit val ec: ExecutionContext)
    extends BaseController(authenticator, controllerComponents) {

  def extractSiret() = Act.secured.admins.async(parse.json) { request =>
    val maybeWebsite = (request.body \ "website").asOpt[String]

    maybeWebsite match {
      case Some(website) =>
        siretExtractorService
          .extractSiret(website)
          .flatMap { response =>
            response.body match {
              case Left(body) => Future.successful(Status(response.code.code)(body.getMessage))
              case Right(body) =>
                logger.debug(s"Saving siret extraction result (${body.status}) in DB before returning")
                siretExtractionRepository.insertOrReplace(body).map(_ => Status(response.code.code)(Json.toJson(body)))
            }
          }
      case None => Future.successful(BadRequest)
    }

  }

}
