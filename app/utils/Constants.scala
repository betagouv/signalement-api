package utils

import models.{UserRole, UserRoles}
import play.api.libs.json.Reads._
import play.api.libs.json._

object Constants {

  object StatusPro {

    case class StatusProValue(val value: String, isFinal: Boolean = false)

    object StatusProValue {
      implicit val statusProValueWrites = new Writes[StatusProValue] {
        def writes(statusProValue: StatusProValue) = Json.toJson(statusProValue.value)
      }
      implicit val statusProValueReads: Reads[StatusProValue] =
        JsPath.read[String].map(fromValue(_).get)

    }

    object A_TRAITER extends StatusProValue("À traiter")
    object NA extends StatusProValue("NA", true)
    object TRAITEMENT_EN_COURS extends StatusProValue("Traitement en cours")
    object SIGNALEMENT_TRANSMIS extends StatusProValue("Signalement transmis")
    object PROMESSE_ACTION extends StatusProValue("Promesse action", true)
    object SIGNALEMENT_INFONDE extends StatusProValue("Signalement infondé", true)
    object SIGNALEMENT_NON_CONSULTE extends StatusProValue("Signalement non consulté", true)
    object SIGNALEMENT_CONSULTE_IGNORE extends StatusProValue("Signalement consulté ignoré", true)

    val status = Seq(
      A_TRAITER,
      NA,
      TRAITEMENT_EN_COURS,
      SIGNALEMENT_TRANSMIS,
      PROMESSE_ACTION,
      SIGNALEMENT_INFONDE,
      SIGNALEMENT_NON_CONSULTE,
      SIGNALEMENT_CONSULTE_IGNORE
    )

    val statusDGCCRF = Seq(
      NA,
      TRAITEMENT_EN_COURS,
      PROMESSE_ACTION,
      SIGNALEMENT_INFONDE,
      SIGNALEMENT_NON_CONSULTE,
      SIGNALEMENT_CONSULTE_IGNORE
    )

    def fromValue(value: String) = status.find(_.value == value)

    def getStatusProWithUserRole(statusPro: Option[StatusProValue], userRole: UserRole) = {

      statusPro.map(status => (statusDGCCRF.contains(status), userRole) match {
        case (false, UserRoles.DGCCRF) => TRAITEMENT_EN_COURS
        case (_, _) => status
      })
    }

    def getSpecificsStatusProWithUserRole(statusPro: Option[String], userRole: UserRole) = {

      statusPro.map(status => (status, userRole) match {
        case (TRAITEMENT_EN_COURS.value, UserRoles.DGCCRF) => StatusPro.status.filter(s => !statusDGCCRF.contains(s)).map(_.value)
        case (_, _) => List(status)
      }).getOrElse(List())
    }


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
    object CONTACT_EMAIL extends ActionEventValue("Envoi d'un email")

    object CONTACT_COURRIER extends ActionEventValue("Envoi d'un courrier")
    object ENVOI_SIGNALEMENT extends ActionEventValue("Envoi du signalement")
    object REPONSE_PRO_SIGNALEMENT extends ActionEventValue("Réponse du professionnel au signalement", true)
    object RETOUR_COURRIER extends ActionEventValue("Retour de courrier")
    object REPONSE_PRO_CONTACT extends ActionEventValue("Réponse du professionnel au contact", true)
    object NON_CONSULTE extends ActionEventValue("Signalement non consulté")
    object CONSULTE_IGNORE extends ActionEventValue("Signalement consulté ignoré")

    object EMAIL_AR extends ActionEventValue("Envoi email accusé de réception")
    object EMAIL_NON_PRISE_EN_COMPTE extends ActionEventValue("Envoi email de non prise en compte")
    object EMAIL_TRANSMISSION extends ActionEventValue("Envoi email d'information de transmission")
    object EMAIL_REPONSE_PRO extends ActionEventValue("Envoi email de la réponse pro")

    object MODIFICATION_COMMERCANT extends ActionEventValue("Modification du commerçant")
    object MODIFICATION_CONSO extends ActionEventValue("Modification du consommateur")

    object COMMENT extends ActionEventValue("Ajout d'un commentaire")
    object COMMENT_DGCCRF extends ActionEventValue("Ajout d'un commentaire interne à la DGCCRF")
    object CONTROL extends ActionEventValue("Contrôle effectué")

    val actionEvents = Seq(
      A_CONTACTER,
      HORS_PERIMETRE,
      CONTACT_EMAIL,
      CONTACT_COURRIER,
      ENVOI_SIGNALEMENT,
      REPONSE_PRO_SIGNALEMENT,
      NON_CONSULTE,
      CONSULTE_IGNORE,
      EMAIL_AR,
      EMAIL_NON_PRISE_EN_COMPTE,
      EMAIL_TRANSMISSION,
      EMAIL_REPONSE_PRO,
      MODIFICATION_COMMERCANT,
      MODIFICATION_CONSO,
      COMMENT,
      COMMENT_DGCCRF,
      CONTROL
    )

    val actionsAdmin = Seq(
      CONTACT_COURRIER,
      COMMENT
    )

    val actionsDGCCRF = Seq(
      CONTROL,
      COMMENT_DGCCRF
    )

    def fromValue(value: String) = actionEvents.find(_.value == value)
  }

  object Departments {

    val AURA = List("01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74")
    val BretagneFrancheComte = List("21", "25", "39", "58", "70", "71", "89", "90")
    val Bretagne = List("22", "29", "35", "56")
    val CDVL = List("18", "28", "36", "37", "41", "45")
    val CollectivitesOutreMer = List("975", "977", "978", "984", "986", "987", "988", "989")
    val Corse = List("2A", "2B")
    val GrandEst = List("08", "10", "51", "52", "54", "55", "57", "67", "68", "88")
    val Guadeloupe = List("971")
    val Guyane = List("973")
    val HautsDeFrance = List("02", "59", "60", "62", "80")
    val IleDeFrance = List("75", "77", "78", "91", "92", "93", "94", "95")
    val LaReunion = List("974")
    val Martinique = List("972")
    val Mayotte = List("976")
    val Normandie = List("14", "27", "50", "61", "76")
    val NouvelleAquitaine = List("16", "17", "19", "23", "24", "33", "40", "47", "64", "79", "86", "87")
    val OCC = List("09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82")
    val PaysDeLaLoire = List("44", "49", "53", "72", "85")
    val ProvenceAlpesCoteAzur = List("04", "05", "06", "13", "83", "84")

    val ALL = AURA ++ BretagneFrancheComte ++ Bretagne ++ CDVL ++ CollectivitesOutreMer ++ Corse ++
     GrandEst ++ Guadeloupe ++ Guyane ++ HautsDeFrance ++ IleDeFrance ++ LaReunion ++ Martinique ++ Mayotte ++
      Normandie ++ NouvelleAquitaine ++ OCC ++ PaysDeLaLoire ++ ProvenceAlpesCoteAzur

    val AUTHORIZED = AURA ++ CDVL ++ OCC
  }

}
