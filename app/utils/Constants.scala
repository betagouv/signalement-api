package utils

import play.api.libs.json.Reads._
import play.api.libs.json._

object Constants {

  object StatusPro {

    // Valeurs possibles de status_pro
    case class StatusProValue(val value: String)

    object StatusProValue {
      implicit val statusProValueWrites = new Writes[StatusProValue] {
        def writes(statusProValue: StatusProValue) = Json.toJson(statusProValue.value)
      }
      implicit val statusProValueReads: Reads[StatusProValue] =
        JsPath.read[String].map(fromValue(_).get)

    }

    object A_TRAITER extends StatusProValue("À traiter")
    object NA extends StatusProValue("NA")
    object TRAITEMENT_EN_COURS extends StatusProValue("Traitement en cours")
    object A_TRANSFERER_SIGNALEMENT extends StatusProValue("À transférer signalement")
    object SIGNALEMENT_TRANSMIS extends StatusProValue("Signalement transmis")
    object SIGNALEMENT_REFUSE extends StatusProValue("Signalement refusé")
    object ADRESSE_INCORRECTE extends StatusProValue("Adresse postale incorrecte")
    object PROMESSE_ACTION extends StatusProValue("Promesse action")
    object PROMESSE_ACTION_REFUSEE extends StatusProValue("Pas de promesse d'action")


    val status = Seq(
      A_TRAITER,
      NA,
      TRAITEMENT_EN_COURS,
      A_TRANSFERER_SIGNALEMENT,
      SIGNALEMENT_TRANSMIS,
      SIGNALEMENT_REFUSE,
      ADRESSE_INCORRECTE,
      PROMESSE_ACTION,
      PROMESSE_ACTION_REFUSEE
    )

    def fromValue(value: String) = status.find(_.value == value)

  }

  object StatusConso {

    // Valeurs possibles de action de la table Event
    case class StatusConsoValue(value: String)

    object StatusConsoValue {
      implicit val statusConsoValueWrites = new Writes[StatusConsoValue] {
        def writes(statusConsoValue: StatusConsoValue) = Json.toJson(statusConsoValue.value)
      }
      implicit val statusConsoValueReads: Reads[StatusConsoValue] =
        JsPath.read[String].map(fromValue(_).get)
    }

    object EN_ATTENTE extends StatusConsoValue("En attente")
    object A_RECONTACTER extends StatusConsoValue("À recontacter")
    object A_INFORMER_TRANSMISSION extends StatusConsoValue("À informer transmission")
    object A_INFORMER_REPONSE_PRO extends StatusConsoValue("À informer réponse pro")
    object FAIT extends StatusConsoValue("Fait")

    val status = Seq(
      EN_ATTENTE,
      A_RECONTACTER,
      A_INFORMER_TRANSMISSION,
      A_INFORMER_REPONSE_PRO,
      FAIT
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
    object RECTIF extends EventTypeValue("RECTIF")

    val eventTypes = Seq(
      PRO,
      CONSO,
      DGCCRF,
      RECTIF
    )

    def fromValue(value: String) = eventTypes.find(_.value == value)

  }

  object ActionEvent {

    case class ActionEventValue(val value: String, val withResult: Boolean = false)

    object ActionEventValue {
      implicit val actionEventValueWrites = new Writes[ActionEventValue] {
        def writes(actionEventValue: ActionEventValue) = Json.obj("name" -> actionEventValue.value, "withResult" -> actionEventValue.withResult)
      }
      implicit val actionEventValueReads: Reads[ActionEventValue] =
        JsPath.read[String].map(fromValue(_).get)
    }

    object A_CONTACTER extends ActionEventValue("À contacter")
    object HORS_PERIMETRE extends ActionEventValue("Hors périmètre")
    object CONTACT_TEL extends ActionEventValue("Appel téléphonique", true)
    object CONTACT_EMAIL extends ActionEventValue("Envoi d'un email")
    object CONTACT_COURRIER extends ActionEventValue("Envoi d'un courrier")
    object RETOUR_COURRIER extends ActionEventValue("Retour de courrier")
    object REPONSE_PRO_CONTACT extends ActionEventValue("Réponse du professionnel au contact", true)
    object ENVOI_SIGNALEMENT extends ActionEventValue("Envoi du signalement")
    object REPONSE_PRO_SIGNALEMENT extends ActionEventValue("Réponse du professionnel au signalement", true)

    object EMAIL_AR extends ActionEventValue("Envoi email accusé de réception")
    object EMAIL_NON_PRISE_EN_COMPTE extends ActionEventValue("Envoi email de non prise en compte")
    object EMAIL_TRANSMISSION extends ActionEventValue("Envoi email d'information de transmission")
    object EMAIL_REPONSE_PRO extends ActionEventValue("Envoi email de la réponse pro")

    object MODIFICATION_COMMERCANT extends ActionEventValue("Modification du commerçant")
    object MODIFICATION_CONSO extends ActionEventValue("Modification du consommateur")

    object COMMENT extends ActionEventValue("Ajout d'un commentaire interne à la DGCCRF")
    object CONTROL extends ActionEventValue("Contrôle effectué")

    val actionPros = Seq(
      A_CONTACTER,
      HORS_PERIMETRE,
      CONTACT_TEL,
      CONTACT_EMAIL,
      CONTACT_COURRIER,
      RETOUR_COURRIER,
      REPONSE_PRO_CONTACT,
      ENVOI_SIGNALEMENT,
      REPONSE_PRO_SIGNALEMENT
    )

    val actionConsos = Seq(
      EMAIL_AR,
      EMAIL_NON_PRISE_EN_COMPTE,
      EMAIL_TRANSMISSION,
      EMAIL_REPONSE_PRO
    )

    val actionRectifs = Seq(
      MODIFICATION_COMMERCANT,
      MODIFICATION_CONSO
    )

    val actionAgents = Seq(
      COMMENT,
      CONTROL
    )

    def fromValue(value: String) = (actionPros ++ actionConsos ++ actionRectifs ++ actionAgents).find(_.value == value)
  }

  object Departments {

    val AURA = List("01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74")
    val CDVL = List("18", "28", "36", "37", "41", "45")
    val OCC = List("09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82")

    val AUTHORIZED = AURA ++ CDVL ++ OCC
  }

}
