package models.company

import play.api.libs.json.Json
import play.api.libs.json.OWrites

case class ProCompanies(
    headOfficesAndSubsidiaries: Map[Company, List[Company]],
    loneSubsidiaries: List[Company]
)

case class ProCompaniesWithAccesses(
    headOfficesAndSubsidiaries: Map[CompanyWithAccess, List[CompanyWithAccess]],
    loneSubsidiaries: List[CompanyWithAccess]
) {
  def toSimpleList: List[CompanyWithAccess] =
    (headOfficesAndSubsidiaries.keys ++
      headOfficesAndSubsidiaries.values.flatten ++
      loneSubsidiaries).toList
}

object ProCompaniesWithAccesses {
  implicit val writes: OWrites[ProCompaniesWithAccesses] = obj =>
    Json.obj(
      "headOfficesAndSubsidiaries" ->
        // JSON-friendly way of outputting the map
        Json.toJson(obj.headOfficesAndSubsidiaries.map { case (headOffice, subsidiaries) =>
          Json.obj(
            "headOffice"   -> Json.toJson(headOffice),
            "subsidiaries" -> Json.toJson(subsidiaries)
          )
        }),
      "loneSubsidiaries" -> Json.toJson(obj.loneSubsidiaries)
    )
}
