package models.report.sampledata

import models.company.Address
import models.company.Company
import utils.SIREN
import utils.SIRET

import java.util.UUID
import scala.util.Random

object CompanyGenerator {

  private def randomCompany(
      siren: SIREN,
      name: String,
      address: Address,
      isHeadOffice: Boolean
  ): Company =
    Company(
      id = UUID.randomUUID(),
      siret = SIRET(siren.value + f"${Random.nextInt(100000)}%05d"),
      name = name,
      address = address,
      activityCode = Random.shuffle(List("40.7Z", "12.4A", "62.01Z")).headOption,
      isHeadOffice = isHeadOffice,
      isOpen = true,
      isPublic = true,
      brand = Some(s"Marque ${name}"),
      commercialName = Some(s"Nom commercial ${name}"),
      establishmentCommercialName = Some(s"Nom établissement commercial ${name}")
    )

  def buildLoneCompany(name: String, isHeadOffice: Boolean = true) = {
    val randomSiren = SIREN(getRandomSiren)
    randomCompany(
      siren = randomSiren,
      name = name,
      address = randomFrenchAddress(),
      isHeadOffice = isHeadOffice
    )
  }

  def buildHeadOfficeAndThreeSubsidiaries(baseName: String, siren: String): (Company, Company, Company, Company) = {
    val headOffice = randomCompany(
      siren = SIREN(siren),
      name = s"$baseName UNLIMITED",
      address = randomFrenchAddress(),
      isHeadOffice = true
    )
    def buildSubsidiary(n: Int) =
      randomCompany(
        siren = SIREN(siren),
        name = s"$baseName FILIALE #$n",
        address = randomFrenchAddress(),
        isHeadOffice = false
      )
    (headOffice, buildSubsidiary(1), buildSubsidiary(2), buildSubsidiary(3))
  }

  def buildMegacorpCompanyAndSubsidiaries() = {
    val (a, b, c, d) = buildHeadOfficeAndThreeSubsidiaries("MEGACORP", getRandomSiren)
    List(a, b, c, d)
  }

  def getRandomSiren = (100000000 + Random.nextInt(900000000)).toString

  private def randomFrenchAddress(): Address =
    Address(
      number = Some("789"),
      street = Some("Rue de la Paix"),
      addressSupplement = Some("Appartement 12, Bâtiment C"),
      postalCode = Some("75008"),
      city = Some("Paris"),
      country = None
    )
}
