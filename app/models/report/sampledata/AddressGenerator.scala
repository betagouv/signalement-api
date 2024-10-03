package models.report.sampledata

import models.company.Address
import utils.Country.Espagne

object AddressGenerator {

  def frenchAddress(): Address =
    Address(
      number = Some("789"),
      street = Some("Rue de la Paix"),
      addressSupplement = Some("Appartement 12, Bâtiment C"),
      postalCode = Some("75008"),
      city = Some("Paris"),
      country = None
    )

  def domTomAddress(): Address =
    Address(
      number = Some("12"),
      street = Some("Rue du Lagon"),
      addressSupplement = Some("Résidence Les Tropiques, Appartement 4B"),
      postalCode = Some("97100"),
      city = Some("Basse-Terre"),
      country = None
    )

  def foreignAddress(): Address =
    Address(
      number = Some("456"),
      street = Some("Calle Falsa"),
      addressSupplement = Some("Piso 3, Puerta B"),
      postalCode = Some("28080"),
      city = Some("Madrid"),
      country = Some(Espagne)
    )
}
