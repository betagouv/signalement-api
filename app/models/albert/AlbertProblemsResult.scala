package models.albert

import play.api.libs.json.Json
import play.api.libs.json.OFormat

case class AlbertProblemsResult(
    nbReportsUsed: Int,
    problemsFound: Seq[AlbertProblem]
)

object AlbertProblemsResult {
  implicit val format: OFormat[AlbertProblemsResult] = Json.format[AlbertProblemsResult]

}

case class AlbertProblem(
    probleme: String,
    signalements: Int
)
object AlbertProblem {
  implicit val format: OFormat[AlbertProblem] = Json.format[AlbertProblem]

}
