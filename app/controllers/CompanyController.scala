package controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.ws._
import play.api.mvc.{ResponseHeader, Result}
import play.api.libs.json._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

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

  def getAllNearbyCompanies(lat: String, long: String, radius: Double, maxCount: Int) = Action.async { implicit request =>

    logger.debug(s"getAllNearbyCompanies [$lat, $long, $radius, $maxCount]")
    val startTime = System.currentTimeMillis

    val MAX_COUNT = 100
    val MAX_PAGE = 10

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
                  val companyLat = (company("latitude")).as[String]
                  val companyLong = (company("longitude")).as[String]
                  val distance = haversineDistance(lat.toDouble, long.toDouble, companyLat.toDouble, companyLong.toDouble)
                  val companyWithDistance = company ++ Json.obj("distance" -> distance)
                  companiesWithDistance += companyWithDistance
                })
                val sortedCompanies = companiesWithDistance.sortWith((d1, d2) => d1("distance").as[Double] < d2("distance").as[Double])
                val numberOfCompanies = sortedCompanies.length;
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