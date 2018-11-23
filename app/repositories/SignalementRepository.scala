package repositories

import java.sql.Date
import java.time.LocalDate
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
    def precisionAnomalie = column[Option[String]]("precision_anomalie")
    def nomEtablissement = column[String]("nom_etablissement")
    def adresseEtablissement = column[String]("adresse_etablissement")
    def dateConstat= column[Date]("date_constat")
    def heureConstat = column[Option[Int]]("heure_constat")
    def description = column[Option[String]]("description")
    def prenom = column[String]("prenom")
    def nom = column[String]("nom")
    def email = column[String]("email")
    def accordContact = column[Boolean]("accord_contact")
    def ticketFileId = column[Option[Long]]("ticket_file_id")
    def anomalieFileId = column[Option[Long]]("anomalie_file_id")

    type SignalementData = (UUID, String, String, Option[String], String, String, Date, Option[Int], Option[String], String, String, String, Boolean, Option[Long], Option[Long])

    def constructSignalement: SignalementData => Signalement = {
      case (id, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement,
      dateConstat, heureConstat, description, prenom, nom, email, accordContact, ticketFileId, anomalieFileId) =>
        Signalement(id, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement,
          dateConstat.toLocalDate, heureConstat, description, prenom, nom, email, accordContact, ticketFileId, anomalieFileId)
    }

    def extractSignalement: PartialFunction[Signalement, SignalementData] = {
      case Signalement(id, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement,
      dateConstat, heureConstat, description, prenom, nom, email, accordContact, ticketFileId, anomalieFileId) =>
        (id, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement,
          Date.valueOf(dateConstat), heureConstat, description, prenom, nom, email, accordContact, ticketFileId, anomalieFileId)
    }

    def * =
      (id, typeEtablissement, categorieAnomalie, precisionAnomalie, nomEtablissement, adresseEtablissement,
        dateConstat, heureConstat, description, prenom, nom, email, accordContact, ticketFileId, anomalieFileId) <> (constructSignalement, extractSignalement.lift)
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
