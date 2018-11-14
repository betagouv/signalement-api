package repositories

import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.Signalement
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * A repository for control.
 *
 * @param dbConfigProvider The Play db config provider. Play will inject this for you.
 */
@Singleton
class SignalementRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  private class SignalementTable(tag: Tag) extends Table[Signalement](tag, "signalement") {

    def id = column[UUID]("id", O.PrimaryKey)
    def typeEtablissement = column[String]("type_etablissement")
    def categorieAnomalie = column[String]("categorie_etablissement")
    def precisionAnomalie = column[String]("precision_anomalie")
    def nomEtablissement = column[String]("nom_etablissement")
    def adresseEtablissement = column[String]("adresse_etablissement")
    def description = column[Option[String]]("description")
    def prenom = column[String]("prenom")
    def nom = column[String]("nom")
    def email = column[String]("email")
    def photo = column[Option[String]]("photo")

    type SignalementData = (Option[UUID], String, String, String, String, String, Option[String], String, String, String, Option[String])

    def * =
      (id.?, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement, description, prenom, nom, email, photo) <> (
        (Signalement.apply _).tupled, Signalement.unapply
      )

  }

  private val signalementTableQuery = TableQuery[SignalementTable]
  
  def create(signalement: Signalement): Future[Signalement] = db
    .run(signalementTableQuery += signalement.copy(id = Some(UUID.randomUUID())))
    .map(id => signalement)
}
