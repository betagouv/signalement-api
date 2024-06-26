package utils.letter

import models.company.Address
import models.company.Company


/**
 * La nouvelle norme du cadre adresse impose l’utilisation des polices suivantes en
 * MAJUSCULES sans caractères spéciaux ou accentués :
 * Expéditeur : Arial, taille 8, style normal, alignée à gauche (dans le cadre réservé à
 * l’adresse expéditeur) ;
 * Destinataire : OCR-B 10 BT, taille 10, style normal, alignée à gauche (dans le cadre
 * réservé à l’adresse destinataire).
 * Remarques :
 * La police OCR-B 10 BT à utiliser peut vous être transmise par le Bureau SI2. En cas de difficulté avec la police OCR-B 10 BT,
 * la police ARIAL pourra être utilisée en concertation avec le Bureau SI2.
 * La taille 9 de la police OCR-B 10 BT pourra être utilisée si le nombre de caractères sur une ligne d’adresse est compris entre
 * 36 et 38, 38 étant le nombre maximal de caractères pour une ligne d’adresse.
 * L’adresse du destinataire doit être écrite en continu avec un saut de ligne à chaque ligne adresse.
 * Une adresse doit être comprise entre trois et six lignes, soit avec un code postal (sans séparateur), soit un pays avec
 * uniquement le nom du pays sur la dernière ligne
 */
object ImprimFipMail {

  private[letter] val RecipientLineLength = 36
  private[letter] val PostalCodeSize      = 5
  private[letter] val MaxCityNameSize     = RecipientLineLength - (PostalCodeSize + 1)

  private[letter] val RecipientMaxLineCount    = 6
  private[letter] val StreetMaxLineCount       = 4
  private[letter] val StreetOptimizedLineCount = 3
  private[letter] val CityMaxLineCount         = 1
  private[letter] val CompanyNameMaxLineCount  = RecipientMaxLineCount - StreetMaxLineCount

  private def splitIntoRecipientCleanedLineSize(input: String, maxLength: Int): List[String] = {
    // Split the input string into words
    val words = input.split("\\s+")

    // Iterate through words and construct the result list
    val result = words.foldLeft(List.empty[String]) { (acc, word) =>
      if (acc.isEmpty) List(word) // Start a new group with the first word
      else {
        val lastGroup = acc.last
        if (lastGroup.length + 1 + word.length <= maxLength) {
          acc.init :+ s"$lastGroup $word" // Add to the current group
        } else {
          acc :+ word // Start a new group
        }
      }
    }
    result.map(_.trim) // Trim any leading/trailing whitespace
  }

  implicit class CompanyImprimFitOps(company: Company) {

    def toRecipientLines: Seq[String] = {

      val streetRecipientLines = company.address.streetRecipientLines

      val companyNameMaxLineCount =
        RecipientMaxLineCount - math.min(
          streetRecipientLines.length,
          StreetOptimizedLineCount
        ) - CityMaxLineCount // to take city line in account

      val companyNameLines =
        splitIntoRecipientCleanedLineSize(company.name.trim, RecipientLineLength).take(companyNameMaxLineCount)

      val brandLines = splitIntoRecipientCleanedLineSize(company.brand.getOrElse("").trim, RecipientLineLength)
        .take(companyNameMaxLineCount - companyNameLines.length)

      val companyNameRecipientLines = (companyNameLines ++ brandLines)
        .filter(_ != "")

      val optimizedStreetRecipientLines =
        if (
          companyNameRecipientLines.length == CompanyNameMaxLineCount && streetRecipientLines.length == StreetMaxLineCount
        ) {
          streetRecipientLines.take(StreetOptimizedLineCount)
        } else { streetRecipientLines }

      (companyNameRecipientLines ++ optimizedStreetRecipientLines :+ company.address.cityRecipientLine)
        .map(_.toUpperCase)
    }

  }

  implicit class AddressImprimFitOps(address: Address) {

    private[letter] def cityRecipientLine: String =
      (address.postalCode.getOrElse("") + " " + address.city.getOrElse("").trim().take(MaxCityNameSize)).trim()

    private[letter] def fullStreet: String = (address.number.getOrElse("") + " " + address.street.getOrElse("")).trim()

    def streetRecipientLines: Seq[String] = {

      val fullStreetLines = splitIntoRecipientCleanedLineSize(fullStreet, RecipientLineLength).take(StreetMaxLineCount)
      val addressSupplementLines =
        splitIntoRecipientCleanedLineSize(address.addressSupplement.getOrElse(""), RecipientLineLength)
          .take(StreetMaxLineCount - fullStreetLines.length)

      (fullStreetLines ++ addressSupplementLines).filter(_ != "").map(_.toUpperCase)

    }

  }

}
