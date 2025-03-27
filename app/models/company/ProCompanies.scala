package models.company

import play.api.libs.json.Json
import play.api.libs.json.OWrites

// Structure to organize all the companies visible by a pro
// the generic type A would typically be a Company
// but it could also be any variant of Company with additional fields
case class ProCompanies[A](
    headOfficesAndSubsidiaries: Map[A, List[A]],
    loneSubsidiaries: List[A]
) {
  def toSimpleList: List[A] =
    (headOfficesAndSubsidiaries.keys ++
      headOfficesAndSubsidiaries.values.flatten ++
      loneSubsidiaries).toList

  def map[B](fn: A => B): ProCompanies[B] =
    ProCompanies(
      headOfficesAndSubsidiaries = this.headOfficesAndSubsidiaries.map { case (key, values) =>
        fn(key) -> values.map(fn)
      },
      loneSubsidiaries = this.loneSubsidiaries.map(fn)
    )
}

object ProCompanies {
  // custom JSON to
  // - output the Map in a JSON-friendly way
  // - sort everything
  implicit val writes: OWrites[ProCompanies[CompanyWithAccessAndCounts]] = obj => {
    def sortCriterion(c: CompanyWithAccessAndCounts) = (c.reportsCount, c.company.name, c.company.siret.value)
    Json.obj(
      "headOfficesAndSubsidiaries" ->
        Json.toJson(
          obj.headOfficesAndSubsidiaries.toList
            .sortBy(x => sortCriterion(x._1))
            .map { case (headOffice, subsidiaries) =>
              Json.obj(
                "headOffice"   -> Json.toJson(headOffice),
                "subsidiaries" -> Json.toJson(subsidiaries.sortBy(sortCriterion))
              )
            }
        ),
      "loneSubsidiaries" -> Json.toJson(obj.loneSubsidiaries.sortBy(sortCriterion))
    )
  }
}
