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
    def dateDernierTraitementEtablissement = column[String]("datederniertraitementetablissement")
    def complementAdresseEtablissement = column[String]("complementadresseetablissement")
    def numeroVoieEtablissement = column[String]("numerovoieetablissement")
    def indiceRepetitionEtablissement = column[String]("indicerepetitionetablissement")
    def typeVoieEtablissement = column[String]("typevoieetablissement")
    def libelleVoieEtablissement = column[String]("libellevoieetablissement")
    def codePostalEtablissement = column[String]("codepostaletablissement")
    def libelleCommuneEtablissement = column[String]("libellecommuneetablissement")
    def libelleCommuneEtrangerEtablissement = column[String]("libellecommuneetrangeretablissement")
    def distributionSpecialeEtablissement = column[String]("distributionspecialeetablissement")
    def codeCommuneEtablissement = column[String]("codecommuneetablissement")
    def codeCedexEtablissement = column[String]("codecedexetablissement")
    def libelleCedexEtablissement = column[String]("libellecedexetablissement")
    def denominationUsuelleEtablissement = column[String]("denominationusuelleetablissement")

    def * = (
      id, siret, dateDernierTraitementEtablissement, complementAdresseEtablissement, numeroVoieEtablissement, indiceRepetitionEtablissement, typeVoieEtablissement,
      libelleVoieEtablissement, codePostalEtablissement, libelleCommuneEtablissement, libelleCommuneEtrangerEtablissement, distributionSpecialeEtablissement,
      codeCommuneEtablissement, codeCedexEtablissement, libelleCedexEtablissement, denominationUsuelleEtablissement)<> (CompanyData.tupled, CompanyData.unapply)
  }

  val companyDataTableQuery = TableQuery[CompanyDataTable]


  def search(q: String, postalCode: String): Future[List[CompanyData]] =
    db.run(companyDataTableQuery
      .filter( c => {toTsVector(c.denominationUsuelleEtablissement) @@ plainToTsQuery(q.bind)})
      .filter(_.codePostalEtablissement === postalCode)
      .to[List].result)
}
