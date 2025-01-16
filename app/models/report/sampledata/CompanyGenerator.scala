package models.report.sampledata

import models.company.Address
import models.company.Company
import utils.SIREN
import utils.SIRET

import java.util.UUID
import scala.collection.mutable.ListBuffer
import scala.util.Random

object CompanyGenerator {

  private def randomCompany(
      siren: SIREN,
      name: String,
      address: Address,
      isHeadOffice: Boolean,
      isOpen: Boolean,
      isPublic: Boolean
  ): Company =
    Company(
      id = UUID.randomUUID(),
      siret = SIRET(siren.value + f"${Random.nextInt(100000)}%05d"),
      name = name,
      address = address,
      activityCode = Random.shuffle(List("40.7Z", "12.4A", "62.01Z")).headOption,
      isHeadOffice = isHeadOffice,
      isOpen = isOpen,
      isPublic = isPublic,
      brand = Some(s"Marque ${name}"),
      commercialName = Some(s"Nom commercial ${name}"),
      establishmentCommercialName = Some(s"Nom établissement commercial ${name}")
    )

  def createLoneCompany(name: String) = {
    val randomSiren = SIREN((100000000 + Random.nextInt(900000000)).toString)
    randomCompany(
      siren = randomSiren,
      name = name,
      address = AddressGenerator.frenchAddress(),
      isHeadOffice = true,
      isOpen = true,
      isPublic = true
    )
  }

  def createMegacorpCompanyAndSubsidiaries(subsidiaryCount: Int) = {

    val randomSiren = SIREN((100000000 + Random.nextInt(900000000)).toString)
    val headOffice = randomCompany(
      siren = randomSiren,
      name = s"MEGACORP UNLIMITED",
      address = AddressGenerator.frenchAddress(),
      isHeadOffice = true,
      isOpen = true,
      isPublic = true
    )

    val companies = ListBuffer(headOffice)

    for (i <- 1 to subsidiaryCount) {
      val c = randomCompany(
        siren = randomSiren,
        name = s"MEGACORP FILIALE #$i",
        address = AddressGenerator.frenchAddress(),
        isHeadOffice = false,
        isOpen = true,
        isPublic = true
      )
      companies += c
    }

    companies.toList

  }

}
