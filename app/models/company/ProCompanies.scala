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
  implicit val writes: OWrites[ProCompaniesWithAccesses] = Json.writes[ProCompaniesWithAccesses]
}
