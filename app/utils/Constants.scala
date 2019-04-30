package utils

import play.api.libs.json.Reads._
import play.api.libs.json._

object Constants {

  object StatusPro {

    // Valeurs possibles de status_pro
    case class StatusProValue(val value: String)

    object A_TRAITER extends StatusProValue("A TRAITER")
    object NA extends StatusProValue("NA")
    object TRAITEMENT_EN_COURS extends StatusProValue("TRAITEMENT EN COURS")
    object A_RAPPELER extends StatusProValue("A RAPPELER")
    object A_ENVOYER_EMAIL extends StatusProValue("A ENVOYER EMAIL")
    object A_ENVOYER_COURRIER extends StatusProValue("A ENVOYER COURRIER")
    object ATTENTE_REPONSE extends StatusProValue("ATTENTE REPONSE")
    object A_TRANSFERER_SIGNALEMENT extends StatusProValue("A TRANSFERER SIGNALEMENT")
    object SIGNALEMENT_TRANSMIS extends StatusProValue("SIGNALEMENT TRANSMIS")
    object SIGNALEMENT_REFUSE extends StatusProValue("SIGNALEMENT REFUSE")
    object PROMESSE_ACTION extends StatusProValue("PROMESSE ACTION")

    val status = Seq(
      A_TRAITER,
      NA,
      TRAITEMENT_EN_COURS,
      A_RAPPELER,
      A_ENVOYER_EMAIL,
      A_ENVOYER_COURRIER,
      ATTENTE_REPONSE,
      A_TRANSFERER_SIGNALEMENT,
      SIGNALEMENT_TRANSMIS,
      SIGNALEMENT_REFUSE,
      PROMESSE_ACTION
    )

    def fromValue(value: String) = status.find(_.value == value)

  }

  object StatusConso {

    // Valeurs possibles de action de la table Event
    case class StatusConso(value: String)

    object VIDE extends StatusConso("")
    object A_RECONTACTER extends StatusConso("A RECONTACTER")
    object A_INFORMER_TRANSMISSION extends StatusConso("A INFORMER TRANSMISSION")
    object A_INFORMER_REPONSE_PRO extends StatusConso("A INFORMER REPONSE PRO")

    val status = Seq(
      A_RECONTACTER,
      A_INFORMER_TRANSMISSION,
      A_INFORMER_REPONSE_PRO
    )

    def fromValue(value: String) = status.find(_.value == value)
  }


  object EventType {

    // Valeurs possibles de event_type
    case class EventTypeValue(value: String)

    object EventTypeValue {
      implicit val eventTypeValueWrites = new Writes[EventTypeValue] {
        def writes(eventTypeValue: EventTypeValue) = Json.toJson(eventTypeValue.value)
      }
      implicit val eventTypeValueReads: Reads[EventTypeValue] =
        JsPath.read[String].map(fromValue(_).get)
    }

    object PRO extends EventTypeValue("PRO")
    object CONSO extends EventTypeValue("CONSO")
    object DGCCRF extends EventTypeValue("DGCCRF")

    val eventTypes = Seq(
      PRO,
      CONSO,
      DGCCRF
    )

    def fromValue(value: String) = eventTypes.find(_.value == value)

    // Valeurs possibles de result_action de la table Event
    case class ResultActionProValue(value: String)

    object OK extends ResultActionProValue("OK")
    object KO extends ResultActionProValue("KO")

    val resultActions = Seq(
      OK,
      KO
    )

  }

  object ActionEvent {

    case class ActionEventValue(val value: String)

    object ActionEventValue {
      implicit val actionEventValueWrites = new Writes[ActionEventValue] {
        def writes(actionEventValue: ActionEventValue) = Json.toJson(actionEventValue.value)
      }
      implicit val actionEventValueReads: Reads[ActionEventValue] =
        JsPath.read[String].map(fromValue(_).get)
    }

    object A_CONTACTER extends ActionEventValue("A contacter")
    object HORS_PERIMETRE extends ActionEventValue("Hors périmètre")
    object CONTACT_TEL extends ActionEventValue("Appel téléphonique")
    object CONTACT_EMAIL extends ActionEventValue("Envoi d'un email")
    object CONTACT_COURRIER extends ActionEventValue("Envoi d'un courrier")
    object REPONSE_PRO_CONTACT extends ActionEventValue("Réponse du professionnel au contact")
    object ENVOI_SIGNALEMENT extends ActionEventValue("Envoi du signalement")
    object REPONSE_PRO_SIGNALEMENT extends ActionEventValue("Réponse du professionnel au signalement")

    object VIDE extends ActionEventValue("")
    object CONSO_CONTACTE extends ActionEventValue("CONSO CONTACTE")
    object CONSO_INFORME_TRANSMISSION extends ActionEventValue("CONSO INFORME TRANSMISSION")
    object CONSO_INFORME_REPONSE_PRO extends ActionEventValue("CONSO INFORME REPONSE PRO")

    val actionPros = Seq(
      A_CONTACTER,
      HORS_PERIMETRE,
      CONTACT_TEL,
      CONTACT_EMAIL,
      CONTACT_COURRIER,
      REPONSE_PRO_CONTACT,
      ENVOI_SIGNALEMENT,
      REPONSE_PRO_SIGNALEMENT
    )

    val actionConsos = Seq(
      VIDE,
      CONSO_CONTACTE,
      CONSO_INFORME_TRANSMISSION,
      CONSO_INFORME_REPONSE_PRO
    )

    def fromValue(value: String) = (actionPros++actionConsos).find(_.value == value)
  }

}
