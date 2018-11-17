package repositories

import java.util.UUID

import javax.inject.{Inject, Singleton}
import models.Signalement
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignalementRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import dbConfig._
  import profile.api._

  private class SignalementTable(tag: Tag) extends Table[Signalement](tag, "signalement") {

    def id = column[UUID]("id", O.PrimaryKey)
    def typeEtablissement = column[String]("type_etablissement")
    def categorieAnomalie = column[String]("categorie_anomalie")
    def precisionAnomalie = column[String]("precision_anomalie")
    def nomEtablissement = column[String]("nom_etablissement")
    def adresseEtablissement = column[String]("adresse_etablissement")
    def description = column[Option[String]]("description")
    def prenom = column[String]("prenom")
    def nom = column[String]("nom")
    def email = column[String]("email")
    def photoOID = column[Option[Long]]("photo")

    def * =
      (id, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement, description, prenom, nom, email, photoOID) <> (
        (Signalement.apply _).tupled, Signalement.unapply
      )
  }

  private val signalementTableQuery = TableQuery[SignalementTable]

  def create(signalement: Signalement): Future[Signalement] = db
    .run(signalementTableQuery += signalement)
    .map(_ => signalement)


  def update(signalement: Signalement): Future[Signalement] = {
    val queryStat = for (refSignalement <- signalementTableQuery if refSignalement.id === signalement.id)
      yield refSignalement
    db.run(queryStat.update(signalement))
      .map(_ => signalement)
  }
}
