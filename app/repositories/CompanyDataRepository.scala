package repositories

import java.util.UUID

import javax.inject.Inject
import javax.inject.Singleton
import models._
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.jdbc.JdbcProfile
import utils.SIREN
import utils.SIRET

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class CompanyDataRepository @Inject() (@NamedDatabase("company_db") dbConfigProvider: DatabaseConfigProvider)(implicit
    ec: ExecutionContext
) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  class CompanyDataTable(tag: Tag) extends Table[CompanyData](tag, "etablissements") {
    def id = column[UUID]("id", O.PrimaryKey)
    def siret = column[SIRET]("siret")
    def siren = column[SIREN]("siren")
    def dateDernierTraitementEtablissement = column[Option[String]]("datederniertraitementetablissement")
    def etablissementSiege = column[Option[String]]("etablissementsiege")
    def complementAdresseEtablissement = column[Option[String]]("complementadresseetablissement")
    def numeroVoieEtablissement = column[Option[String]]("numerovoieetablissement")
    def indiceRepetitionEtablissement = column[Option[String]]("indicerepetitionetablissement")
    def typeVoieEtablissement = column[Option[String]]("typevoieetablissement")
    def libelleVoieEtablissement = column[Option[String]]("libellevoieetablissement")
    def codePostalEtablissement = column[Option[String]]("codepostaletablissement")
    def libelleCommuneEtablissement = column[Option[String]]("libellecommuneetablissement")
    def libelleCommuneEtrangerEtablissement = column[Option[String]]("libellecommuneetrangeretablissement")
    def distributionSpecialeEtablissement = column[Option[String]]("distributionspecialeetablissement")
    def codeCommuneEtablissement = column[Option[String]]("codecommuneetablissement")
    def codeCedexEtablissement = column[Option[String]]("codecedexetablissement")
    def libelleCedexEtablissement = column[Option[String]]("libellecedexetablissement")
    def denominationUsuelleEtablissement = column[Option[String]]("denominationusuelleetablissement")
    def enseigne1Etablissement = column[Option[String]]("enseigne1etablissement")
    def activitePrincipaleEtablissement = column[String]("activiteprincipaleetablissement")
    def etatAdministratifEtablissement = column[Option[String]]("etatadministratifetablissement")

    def * = (
      id,
      siret,
      siren,
      dateDernierTraitementEtablissement,
      etablissementSiege,
      complementAdresseEtablissement,
      numeroVoieEtablissement,
      indiceRepetitionEtablissement,
      typeVoieEtablissement,
      libelleVoieEtablissement,
      codePostalEtablissement,
      libelleCommuneEtablissement,
      libelleCommuneEtrangerEtablissement,
      distributionSpecialeEtablissement,
      codeCommuneEtablissement,
      codeCedexEtablissement,
      libelleCedexEtablissement,
      denominationUsuelleEtablissement,
      enseigne1Etablissement,
      activitePrincipaleEtablissement,
      etatAdministratifEtablissement
    ) <> (CompanyData.tupled, CompanyData.unapply)
  }

  class CompanyActivityTable(tag: Tag) extends Table[CompanyActivity](tag, "activites") {
    def code = column[String]("code")

    def libelle = column[String]("libelle")

    def * = (code, libelle) <> (CompanyActivity.tupled, CompanyActivity.unapply)
  }

  val companyDataTableQuery = TableQuery[CompanyDataTable]
  val companyActivityTableQuery = TableQuery[CompanyActivityTable]

  private val least = SimpleFunction.binary[Option[Double], Option[Double], Option[Double]]("least")

  private[this] def filterClosedEtablissements(row: CompanyDataTable): Rep[Boolean] =
    row.etatAdministratifEtablissement.getOrElse("A") =!= "F"

  def create(companyData: CompanyData): Future[CompanyData] = db
    .run(companyDataTableQuery += companyData)
    .map(_ => companyData)

  def delete(id: UUID): Future[Int] = db.run {
    companyDataTableQuery
      .filter(_.id === id)
      .delete
  }

  def search(q: String, postalCode: String): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      companyDataTableQuery
        .filter(_.codePostalEtablissement === postalCode)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(filterClosedEtablissements)
        .filter(result =>
          least(
            result.denominationUsuelleEtablissement <-> q,
            result.enseigne1Etablissement <-> q
          ).map(dist => dist < 0.75).getOrElse(false)
        )
        .sortBy(result => least(result.denominationUsuelleEtablissement <-> q, result.enseigne1Etablissement <-> q))
        .take(10)
        .joinLeft(companyActivityTableQuery)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySirets(
      sirets: List[SIRET],
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      companyDataTableQuery
        .filter(_.siret inSetBind sirets)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filterIf(!includeClosed)(filterClosedEtablissements)
        .joinLeft(companyActivityTableQuery)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySiret(
      siret: SIRET,
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] = searchBySirets(List(siret))

  def searchHeadOffices(sirets: List[SIRET]): Future[List[SIRET]] =
    db.run(
      companyDataTableQuery
        .filter(_.siret inSetBind sirets)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(_.etablissementSiege === "true")
        .map(_.siret)
        .to[List]
        .result
    )

  def searchBySiretWithHeadOffice(siret: SIRET): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      companyDataTableQuery
        .filter(_.siren === SIREN(siret))
        .filter(company => company.siret === siret || company.etablissementSiege === "true")
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(filterClosedEtablissements)
        .joinLeft(companyActivityTableQuery)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySirens(
      sirens: List[SIREN],
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      companyDataTableQuery
        .filter(_.siren inSetBind sirens)
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filterIf(!includeClosed)(filterClosedEtablissements)
        .joinLeft(companyActivityTableQuery)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
    )

  def searchBySiren(
      siren: SIREN,
      includeClosed: Boolean = false
  ): Future[List[(CompanyData, Option[CompanyActivity])]] = searchBySirens(List(siren))

  def searchHeadOfficeBySiren(siren: SIREN): Future[Option[(CompanyData, Option[CompanyActivity])]] =
    db.run(
      companyDataTableQuery
        .filter(_.siren === siren)
        .filter(_.etablissementSiege === "true")
        .filter(_.denominationUsuelleEtablissement.isDefined)
        .filter(filterClosedEtablissements)
        .joinLeft(companyActivityTableQuery)
        .on(_.activitePrincipaleEtablissement === _.code)
        .to[List]
        .result
        .headOption
    )
}
