package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.db.slick.DatabaseConfigProvider
import play.db.NamedDatabase
import slick.jdbc.JdbcProfile
import utils.Constants.Departments
import utils.{Address, SIRET}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CompanyDataRepository @Inject()(@NamedDatabase("company_db") dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  class CompanyDataTable(tag: Tag) extends Table[CompanyData](tag, "etablissements") {
    def id = column[UUID]("id", O.PrimaryKey)
    def siret = column[String]("siret")
    def siren = column[String]("siren")
    def dateDernierTraitementEtablissement = column[String]("datederniertraitementetablissement")
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

    def * = (
      id, siret, siren, dateDernierTraitementEtablissement, complementAdresseEtablissement, numeroVoieEtablissement, indiceRepetitionEtablissement, typeVoieEtablissement,
      libelleVoieEtablissement, codePostalEtablissement, libelleCommuneEtablissement, libelleCommuneEtrangerEtablissement, distributionSpecialeEtablissement,
      codeCommuneEtablissement, codeCedexEtablissement, libelleCedexEtablissement, denominationUsuelleEtablissement, enseigne1Etablissement, activitePrincipaleEtablissement)<> (CompanyData.tupled, CompanyData.unapply)
  }

  class CompanyUnitDataTable(tag: Tag) extends Table[CompanyUnitData](tag, "uniteslegales") {
    def id = column[UUID]("id", O.PrimaryKey)
    def siren = column[String]("siren")
    def denominationUniteLegale = column[Option[String]]("denominationunitelegale")

    def * = (id, siren, denominationUniteLegale) <> (CompanyUnitData.tupled, CompanyUnitData.unapply)
  }

  val companyDataTableQuery = TableQuery[CompanyDataTable]
  val companyUnitDataTableQuery = TableQuery[CompanyUnitDataTable]

  private val least = SimpleFunction.binary[Option[Double], Option[Double], Option[Double]]("least")

  def search(q: String, postalCode: String): Future[List[CompanyData]] =
    db.run(companyDataTableQuery
      .filter(_.codePostalEtablissement === postalCode)
      .filter(result => least(
        result.denominationUsuelleEtablissement <-> q,
        result.enseigne1Etablissement <-> q
      ).map(dist => dist < 0.75).getOrElse(false))
      .sortBy(result => least(
        result.denominationUsuelleEtablissement <-> q,
        result.enseigne1Etablissement <-> q)
      )
      .take(10)
      .to[List].result)
}
