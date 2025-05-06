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
  private def buildWrites[A](ordering: Ordering[A])(implicit oWrites: OWrites[A]): OWrites[ProCompanies[A]] = obj =>
    Json.obj(
      "headOfficesAndSubsidiaries" ->
        Json.toJson(
          obj.headOfficesAndSubsidiaries.toList
            .sortBy(_._1)(ordering)
            .map { case (headOffice, subsidiaries) =>
              Json.obj(
                "headOffice"   -> Json.toJson(headOffice),
                "subsidiaries" -> Json.toJson(subsidiaries.sorted(ordering))
              )
            }
        ),
      "loneSubsidiaries" -> Json.toJson(obj.loneSubsidiaries.sorted(ordering))
    )

  implicit val writesForCompanyWithAccessAndCounts: OWrites[ProCompanies[CompanyWithAccessAndCounts]] = buildWrites(
    Ordering.by(c => (-c.reportsCount, -c.ongoingReportsCount, c.company.name, c.company.siret.value))
  )

  implicit val writesForCompanyWithAccess: OWrites[ProCompanies[CompanyWithAccess]] = buildWrites(
    Ordering.by(c => (c.company.name, c.company.siret.value))
  )

}
