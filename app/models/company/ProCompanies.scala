package models.company

case class ProCompanies(
    headOfficesAndSubsidiaries: Map[Company, List[Company]],
    loneSubsidiaries: List[Company]
)

case class ProCompaniesWithAccesses(
    headOfficesAndSubsidiaries: Map[CompanyWithAccess, List[CompanyWithAccess]],
    loneSubsidiaries: List[CompanyWithAccess]
) {
  def flattened: List[CompanyWithAccess] =
    (headOfficesAndSubsidiaries.keys ++
      headOfficesAndSubsidiaries.values.flatten ++
      loneSubsidiaries).toList
}
