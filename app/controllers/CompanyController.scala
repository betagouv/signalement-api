package controllers

import com.mohiva.play.silhouette.api.Silhouette
import javax.inject.Inject
import play.api.Logger
import play.api.libs.ws._
import play.api.mvc.{ResponseHeader, Result}
import play.api.libs.json._
import utils.silhouette.AuthEnv

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

case class Location(lat: Double, lon: Double)

class CompanyController @Inject()(ws: WSClient, val silhouette: Silhouette[AuthEnv])
                                 (implicit val executionContext: ExecutionContext) extends BaseController {

  val logger: Logger = Logger(this.getClass)

  def getCompanies(search: String, postalCode: Option[String], maxCount: Int) = UnsecuredAction.async { implicit request =>

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


  def getCompaniesFromAddok(search: String) = UnsecuredAction.async { implicit request =>

    logger.debug(s"getCompaniesFromAddok [$search]")

    var request = ws
      .url(s"http://poi.addok.xyz/search/?q=$search")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }

  def getCompaniesBySiret(siret: String, maxCount: Int) = UnsecuredAction.async { implicit request =>

    logger.debug(s"getCompaniesBySiret [$siret]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/siret/$siret")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request = request.addQueryStringParameters("per_page" -> maxCount.toString)

    request.get().flatMap(
      response => response.status match {
        case NOT_FOUND => Future(NotFound(response.json))
        case _ => Future(Ok(response.json))
      }
    );

  }

  private def haversineDistance(lat1: Double, long1: Double, lat2: Double, long2: Double): Double = {
    val EARTH_RADIUS = 6378137d // equatorial earth radius in meters
    val dLat = Math.toRadians(lat2 - lat1)
    val dLong = Math.toRadians(long2 - long1)
    val dLatSin = Math.sin(dLat / 2)
    val dLongSin = Math.sin(dLong / 2)
    val a = Math.pow(dLatSin, 2) + (Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(dLongSin, 2))
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    EARTH_RADIUS * c
  }

  private def getNearbyCompanies(lat: String, long: String, radius: Double, maxCount: Int, pageNumber: Int): Future[WSResponse] = {

    logger.debug(s"getNearbyCompanies [$lat, $long, $radius, $maxCount, $pageNumber]")

    var request = ws
      .url(s"https://entreprise.data.gouv.fr/api/sirene/v1/near_point/")
      .addHttpHeaders("Accept" -> "application/json", "Content-Type" -> "application/json")

    request = request.addQueryStringParameters("lat" -> lat)
    request = request.addQueryStringParameters("long" -> long)
    request = request.addQueryStringParameters("radius" -> radius.toString)
    request = request.addQueryStringParameters("per_page" -> maxCount.toString)
    request = request.addQueryStringParameters("page" -> pageNumber.toString)

    request.get()
  }

  def getAllNearbyCompanies(lat: String, long: String, radius: Double, maxCount: Int) = UnsecuredAction.async { implicit request =>

    logger.debug(s"getAllNearbyCompanies [$lat, $long, $radius, $maxCount]")
    val startTime = System.currentTimeMillis

    val MAX_COUNT = 100
    val MAX_PAGE = 10
    val ACTIVITY_DIVISIONS_WHITELIST = List(
      "01", "02", "03", // Section A : Agriculture, sylviculture et pêche
      "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", "31", "32", "33", // Section C : Industrie manufacturière
      "45", "46", "47", // Section G : Commerce ; réparation d'automobiles et de motocycles
      "55", "56" // Section I : Hébergement et restauration
    )

    getNearbyCompanies(lat, long, radius, MAX_COUNT, 1).flatMap(
      firstPage => {
        firstPage.status match {
          case NOT_FOUND => Future(NotFound(firstPage.json))
          case _ => {
            val totalResults = (firstPage.json("total_results")).as[Int]
            logger.debug(s"totalResults $totalResults")
            val totalPages = (firstPage.json("total_pages")).as[Int]
            logger.debug(s"totalPages $totalPages")
            val companies = (firstPage.json("etablissements")).as[List[JsObject]]
            var allCompanies = companies.to[ListBuffer]
            var nextPagesFuture = new ListBuffer[Future[WSResponse]]()
            val numberOfPages = Math.min(totalPages, MAX_PAGE)
            if (numberOfPages > 1) {
              for (pageIndex <- 2 to numberOfPages) {
                nextPagesFuture += getNearbyCompanies(lat, long, radius, MAX_COUNT, pageIndex)
              }
            }
            Future.sequence(nextPagesFuture).flatMap(
              pages => {
                pages.foreach(page => {
                  page.status match {
                    case NOT_FOUND => {}
                    case _ => {  
                      val companies = (page.json("etablissements")).as[List[JsObject]]
                      allCompanies ++= companies
                    }
                  }
                })
                var companiesWithDistance = new ListBuffer[JsObject]()
                allCompanies.foreach(company => {
                  val mainActivity = (company("activite_principale")).as[String]
                  val mainActivityDivision = mainActivity.substring(0, 2);
                  if (ACTIVITY_DIVISIONS_WHITELIST.contains(mainActivityDivision)) {
                    val companyLat = (company("latitude")).as[String]
                    val companyLong = (company("longitude")).as[String]
                    val distance = haversineDistance(lat.toDouble, long.toDouble, companyLat.toDouble, companyLong.toDouble)
                    val companyWithDistance = company ++ Json.obj("distance" -> distance)
                    companiesWithDistance += companyWithDistance
                  }
                })
                val sortedCompanies = companiesWithDistance.sortWith((d1, d2) => d1("distance").as[Double] < d2("distance").as[Double])
                val numberOfCompanies = sortedCompanies.length;
                logger.debug(s"numberOfCompanies $numberOfCompanies")
                val numberOfResults = Math.min(numberOfCompanies, maxCount)
                logger.debug(s"numberOfResults $numberOfResults")
                val nearbyCompanies = sortedCompanies.take(numberOfResults)
                val allNearbyCompaniesResponse = firstPage.json.as[JsObject] ++ Json.obj("total_results" -> numberOfResults) ++ Json.obj("total_pages" -> 1) ++ Json.obj("per_page" -> maxCount) - "etablissements" ++ Json.obj("etablissement" -> nearbyCompanies)
                val duration = (System.currentTimeMillis - startTime) / 1000d
                logger.debug(s"duration ${duration}s")
                Future(Ok(allNearbyCompaniesResponse))
              }
            )
          }
        }
      }
    )

  }

  def getSuggestions(search: String) = UnsecuredAction.async { implicit request =>

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