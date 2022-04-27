package repositories.companydata

import models._
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import repositories.PostgresProfile
import repositories.companydata.CompanyDataRepository.toOptionalSqlValue
import repositories.companydata.CompanyDataTable.DENOMINATION_USUELLE_ETABLISSEMENT
import slick.jdbc.JdbcProfile
import utils.SIREN
import utils.SIRET

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyDataRepository @Inject() (@NamedDatabase("company_db") dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  private val least = SimpleFunction.binary[Option[Double], Option[Double], Option[Double]]("least")

  private[this] def filterClosedEtablissements(row: CompanyDataTable): Rep[Boolean] =
    row.etatAdministratifEtablissement.getOrElse("A") =!= "F"

  def insertAll(companies: Map[String, Option[String]]): DBIO[Int] = {

    val companyKeyValues: Map[String, String] =
      companies.view.mapValues(maybeValue => toOptionalSqlValue(maybeValue)).toMap
    val insertColumns: String = companyKeyValues.keys.mkString(",")
    val insertValues: String = companyKeyValues.values.mkString(",")
    val insertValuesOnSiretConflict: String = companyKeyValues.view
      .filterKeys(_ != DENOMINATION_USUELLE_ETABLISSEMENT)
      .toMap
      .map { case (columnName, value) => s"$columnName = $value" }
      .mkString(",")

    sqlu"""INSERT INTO etablissements (#$insertColumns)
          VALUES (#$insertValues)
          ON CONFLICT(siret) DO UPDATE SET #$insertValuesOnSiretConflict,
          denominationusuelleetablissement=COALESCE(NULLIF(#${companyKeyValues.getOrElse(
        DENOMINATION_USUELLE_ETABLISSEMENT,
        "NULL"
      )}, ''), etablissements.denominationusuelleetablissement)
        """
  }

  def updateName(name: (SIREN, String)): DBIO[Int] =
    CompanyDataTable.table
      .filter(_.siren === name._1)
      .filter(x => x.denominationUsuelleEtablissement.isEmpty || x.denominationUsuelleEtablissement === "")
      .map(_.denominationUsuelleEtablissement)
      .update(Some(name._2))

  def create(companyData: CompanyData): Future[CompanyData] = db
    .run(CompanyDataTable.table += companyData)
    .map(_ => companyData)

  def delete(id: UUID): Future[Int] = db.run {
    CompanyDataTable.table
      .filter(_.id === id)
      .delete
  }

  def search(q: String, postalCode: String): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      CompanyDataTable.table
        .filter(_.codePostalEtablissement === postalCode)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(filterClosedEtablissements)
        .filter(result =>
          least(
            result.denominationUsuelleEtablissement <-> q,
            result.enseigne1Etablissement <-> q
          ).map(dist => dist < 0.68).getOrElse(false)
        )
        .sortBy(result => least(result.denominationUsuelleEtablissement <-> q, result.enseigne1Etablissement <-> q))
        .take(10)
        .joinLeft(CompanyActivityTable.table)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySirets(
      sirets: List[SIRET],
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siret inSetBind sirets)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filterIf(!includeClosed)(filterClosedEtablissements)
        .joinLeft(CompanyActivityTable.table)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySiret(
      siret: SIRET,
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] = searchBySirets(List(siret), includeClosed)

  def filterHeadOffices(sirets: List[SIRET]): Future[List[CompanyData]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siret inSetBind sirets)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(_.etablissementSiege === "true")
        .to[List]
        .result
    )

  def getHeadOffice(siret: SIRET): Future[List[CompanyData]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siren === SIREN(siret))
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(_.etablissementSiege === "true")
        .to[List]
        .result
    )

  def searchBySiretIncludingHeadOfficeWithActivity(siret: SIRET): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siren === SIREN(siret))
        .filter(company => company.siret === siret || company.etablissementSiege === "true")
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(filterClosedEtablissements)
        .joinLeft(CompanyActivityTable.table)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySiretIncludingHeadOffice(siret: SIRET): Future[List[CompanyData]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siren === SIREN(siret))
        .filter(company => company.siret === siret || company.etablissementSiege === "true")
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(filterClosedEtablissements)
        .to[List]
        .result
    )

  def searchBySirens(
      sirens: List[SIREN],
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siren inSetBind sirens)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filterIf(!includeClosed)(filterClosedEtablissements)
        .joinLeft(CompanyActivityTable.table)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySiren(
      siren: SIREN
  ): Future[List[(CompanyData, Option[CompanyActivity])]] =
    searchBySirens(List(siren))

  def searchHeadOfficeBySiren(siren: SIREN): Future[Option[(CompanyData, Option[CompanyActivity])]] =
    searchHeadOfficeBySiren(List(siren)).map(_.headOption)

  def searchHeadOfficeBySiren(
      sirens: List[SIREN],
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      CompanyDataTable.table
        .filter(_.siren inSetBind sirens)
        .filter(_.etablissementSiege === "true")
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filterIf(!includeClosed)(filterClosedEtablissements)
        .joinLeft(CompanyActivityTable.table)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )
}

object CompanyDataRepository {

  def toOptionalSqlValue(maybeValue: Option[String]): String = maybeValue.fold("NULL")(value => toSqlValue(value))

  def toSqlValue(value: String): String = s"'${value.replace("'", "''")}'"

  def toFieldValueMap(companyData: CompanyData): Map[String, Option[String]] =
    Map(
      "id" -> Some(companyData.id.toString),
      "siret" -> Some(companyData.siret.value),
      "siren" -> Some(companyData.siren.value),
      "datederniertraitementetablissement" -> companyData.dateDernierTraitementEtablissement,
      "etablissementsiege" -> companyData.etablissementSiege,
      "complementadresseetablissement" -> companyData.complementAdresseEtablissement,
      "numerovoieetablissement" -> companyData.numeroVoieEtablissement,
      "indicerepetitionetablissement" -> companyData.indiceRepetitionEtablissement,
      "typevoieetablissement" -> companyData.typeVoieEtablissement,
      "libellevoieetablissement" -> companyData.libelleVoieEtablissement,
      "codepostaletablissement" -> companyData.codePostalEtablissement,
      "libellecommuneetablissement" -> companyData.libelleCommuneEtablissement,
      "libellecommuneetrangeretablissement" -> companyData.libelleCommuneEtrangerEtablissement,
      "distributionspecialeetablissement" -> companyData.distributionSpecialeEtablissement,
      "codecommuneetablissement" -> companyData.codeCommuneEtablissement,
      "codecedexetablissement" -> companyData.codeCedexEtablissement,
      "libellecedexetablissement" -> companyData.libelleCedexEtablissement,
      DENOMINATION_USUELLE_ETABLISSEMENT -> companyData.denominationUsuelleEtablissement,
      "enseigne1etablissement" -> companyData.enseigne1Etablissement,
      "activiteprincipaleetablissement" -> Some(companyData.activitePrincipaleEtablissement),
      "etatadministratifetablissement" -> companyData.etatAdministratifEtablissement
    )
}
