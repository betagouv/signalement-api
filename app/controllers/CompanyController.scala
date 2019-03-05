package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.ws._
import play.api.mvc.{ResponseHeader, Result}
import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

case class Location(lat: Double, lon: Double)

class CompanyController @Inject()(ws: WSClient)
                                 (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getCompanies(search: String, postalCode: Option[String], maxCount: Int) = Action.async { implicit request =>

    logger.debug(s"getCompanies [$search, $postalCode, $maxCount]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/full_text/$search")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    if (postalCode.isDefined) {
      request = request.addQueryStringParameters("code_postal" -> postalCode.get)
    }
    
    request = request.addQueryStringParameters("per_page" -> maxCount.toString)

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }

  private def haversineDistance(lat1: Double, long1: Double, lat2: Double, long2: Double): Double = {
    val EARTH_RADIUS = 6378137 // equatorial earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLong = Math.toRadians(long2 - long1)
    val dLatSin = Math.sin(dLat / 2)
    val dLongSin = Math.sin(dLong / 2)
    val a = Math.pow(dLatSin, 2) + (Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(dLongSin, 2))
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    EARTH_RADIUS * c
  }

  def getNearbyCompanies(lat: String, long: String, radius: Double, maxCount: Int) = Action.async { implicit request =>

    logger.debug(s"getNearbyCompanies [$lat, $long, $radius, $maxCount]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/near_point/")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request = request.addQueryStringParameters("lat" -> lat)
    request = request.addQueryStringParameters("long" -> long)
    request = request.addQueryStringParameters("radius" -> radius.toString)
    request = request.addQueryStringParameters("per_page" -> maxCount.toString)

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => {
          Future {
            val companies = (response.json("etablissements")).as[List[JsObject]]
            var companiesWithDistance = new ListBuffer[JsObject]()
            companies.foreach(company => {
              val companyLat = (company("latitude")).as[String]
              val companyLong = (company("longitude")).as[String]
              val distance = haversineDistance(lat.toDouble, long.toDouble, companyLat.toDouble, companyLong.toDouble)
              val companyWithDistance = company ++ Json.obj("distance" -> distance)
              companiesWithDistance += companyWithDistance
            })
            val sortedCompanies = companiesWithDistance.sortWith((d1, d2) => d1("distance").as[Double] < d2("distance").as[Double])
            // etablissements property is replaced by etablissement for API consistency
            val nearbyCompaniesResponse = response.json.as[JsObject] - "etablissements" ++ Json.obj("etablissement" -> sortedCompanies)
            Ok(nearbyCompaniesResponse)
          }
        }
      }
    );

  }

  def getSuggestions(search: String) = Action.async { implicit request =>

    logger.debug(s"getSuggestions [$search]")

    val request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/suggest/$search")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }
}