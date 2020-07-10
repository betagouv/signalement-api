package utils

import models.UserRoles.Pro
import models.{UserRole, UserRoles}
import play.api.libs.json.Reads._
import play.api.libs.json._

object Constants {

  object ReportStatus {

    case class ReportStatusValue(defaultValue: String, valueByRole: Map[UserRole, Option[String]] = Map(), isFinal: Boolean = false) {

      def getValueWithUserRole(userRole: UserRole) = {
        valueByRole.get(userRole).getOrElse(Some(defaultValue))
      }
    }

    object ReportStatusValue {
      implicit def reportStatusValueWrites(implicit userRole: Option[UserRole]) = new Writes[ReportStatusValue] {
        def writes(reportStatusValue: ReportStatusValue) = Json.toJson(
          userRole.flatMap(reportStatusValue.getValueWithUserRole(_)).getOrElse("")
        )
      }
      implicit val reportStatusValueReads: Reads[ReportStatusValue] =
        JsPath.read[String].map(fromDefaultValue(_))
    }

    object NA extends ReportStatusValue(
      "NA",
      Map(
        UserRoles.Pro -> None
      ),
      isFinal = true
    )
    object EMPLOYEE_REPORT extends ReportStatusValue(
      "Lanceur d'alerte",
      Map(
        UserRoles.Pro -> None
      ),
      isFinal = true
    )
    object TRAITEMENT_EN_COURS extends ReportStatusValue(
      "Traitement en cours",
      Map(
        UserRoles.Pro -> Some("Non consulté")
      )
    )
    object SIGNALEMENT_TRANSMIS extends ReportStatusValue(
      "Signalement transmis",
      Map(
        UserRoles.DGCCRF -> Some("Traitement en cours"),
        UserRoles.Pro -> Some("À répondre")
      )
    )
    object PROMESSE_ACTION extends ReportStatusValue(
      "Promesse action",
      Map(
        UserRoles.Pro -> Some("Clôturé")
      ),
      isFinal = true
    )
    object SIGNALEMENT_INFONDE extends ReportStatusValue(
      "Signalement infondé",
      Map(
        UserRoles.Pro -> Some("Clôturé")
      ),
      isFinal = true
    )
    object SIGNALEMENT_NON_CONSULTE extends ReportStatusValue(
      "Signalement non consulté",
      Map(
        UserRoles.Pro -> Some("Clôturé")
      ),
      isFinal = true
    )
    object SIGNALEMENT_CONSULTE_IGNORE extends ReportStatusValue(
      "Signalement consulté ignoré",
      Map(
        UserRoles.Pro -> Some("Clôturé")
      ),
      isFinal = true
    )
    object SIGNALEMENT_MAL_ATTRIBUE extends ReportStatusValue(
      "Signalement mal attribué",
      Map(
        UserRoles.Pro -> Some("Clôturé")
      ),
      isFinal = true
    )

    val reportStatusList = Seq(
      NA,
      EMPLOYEE_REPORT,
      TRAITEMENT_EN_COURS,
      SIGNALEMENT_TRANSMIS,
      PROMESSE_ACTION,
      SIGNALEMENT_INFONDE,
      SIGNALEMENT_NON_CONSULTE,
      SIGNALEMENT_CONSULTE_IGNORE,
      SIGNALEMENT_MAL_ATTRIBUE
    )

    def fromDefaultValue(value: String) = reportStatusList.find(_.defaultValue == value).getOrElse(ReportStatusValue(""))


    def getStatusListForValueWithUserRole(value: Option[String], userRole: UserRole) = {
      value match {
        case Some(value) => reportStatusList.filter(_.getValueWithUserRole(userRole) == Some(value))
        case None => reportStatusList.filter(_.getValueWithUserRole(userRole).isDefined)
      }
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
        JsPath.read[String].map(fromValue(_))
    }

    object PRO extends EventTypeValue("PRO")
    object CONSO extends EventTypeValue("CONSO")
    object DGCCRF extends EventTypeValue("DGCCRF")
    object ADMIN extends EventTypeValue("ADMIN")

    val eventTypes = Seq(
      PRO,
      CONSO,
      DGCCRF,
      ADMIN
    )

    def fromValue(value: String) = eventTypes.find(_.value == value).getOrElse(EventTypeValue(""))

    def fromUserRole(userRole: UserRole) = {
      userRole match {
        case UserRoles.Admin => ADMIN
        case UserRoles.DGCCRF => DGCCRF
        case UserRoles.Pro => PRO
        case _ => CONSO
      }
    }

  }

  object ActionEvent {

    case class ActionEventValue(value: String)

    object ActionEventValue {
      implicit val actionEventValueReads: Reads[ActionEventValue] = JsPath.read[String].map(fromValue(_))
      implicit val actionEventValueWriter = Json.writes[ActionEventValue]
    }

    object A_CONTACTER extends ActionEventValue("À contacter")
    object HORS_PERIMETRE extends ActionEventValue("Hors périmètre")
    object CONTACT_EMAIL extends ActionEventValue("Envoi d'un email")

    object CONTACT_COURRIER extends ActionEventValue("Envoi d'un courrier")
    object ENVOI_SIGNALEMENT extends ActionEventValue("Première consultation du signalement par le professionnel")
    object REPONSE_PRO_SIGNALEMENT extends ActionEventValue("Réponse du professionnel au signalement")
    object REVIEW_ON_REPORT_RESPONSE extends ActionEventValue("Avis du consommateur sur la réponse du professionnel")
    object RETOUR_COURRIER extends ActionEventValue("Retour de courrier")
    object REPONSE_PRO_CONTACT extends ActionEventValue("Réponse du professionnel au contact")
    object NON_CONSULTE extends ActionEventValue("Signalement non consulté")
    object CONSULTE_IGNORE extends ActionEventValue("Signalement consulté ignoré")
    object RELANCE extends ActionEventValue("Relance")

    object EMAIL_AR extends ActionEventValue("Envoi email accusé de réception")
    object EMAIL_NON_PRISE_EN_COMPTE extends ActionEventValue("Envoi email de non prise en compte")
    object EMAIL_TRANSMISSION extends ActionEventValue("Email « signalement consulté » envoyé au consommateur")
    object EMAIL_REPONSE_PRO extends ActionEventValue("Envoi email de la réponse pro")

    object MODIFICATION_COMMERCANT extends ActionEventValue("Modification du commerçant")
    object MODIFICATION_CONSO extends ActionEventValue("Modification du consommateur")

    object COMMENT extends ActionEventValue("Ajout d'un commentaire")
    object CONSUMER_ATTACHMENTS extends ActionEventValue("Ajout de pièces jointes fournies par le consommateur")
    object PROFESSIONAL_ATTACHMENTS extends ActionEventValue("Ajout de pièces jointes fournies par l'entreprise")
    object CONTROL extends ActionEventValue("Contrôle effectué")

    val actionEvents = Seq(
      A_CONTACTER,
      HORS_PERIMETRE,
      CONTACT_EMAIL,
      CONTACT_COURRIER,
      ENVOI_SIGNALEMENT,
      REPONSE_PRO_SIGNALEMENT,
      REVIEW_ON_REPORT_RESPONSE,
      NON_CONSULTE,
      CONSULTE_IGNORE,
      RELANCE,
      EMAIL_AR,
      EMAIL_NON_PRISE_EN_COMPTE,
      EMAIL_TRANSMISSION,
      EMAIL_REPONSE_PRO,
      MODIFICATION_COMMERCANT,
      MODIFICATION_CONSO,
      COMMENT,
      CONSUMER_ATTACHMENTS,
      PROFESSIONAL_ATTACHMENTS,
      CONTROL
    )

    val actionsForUserRole: Map[UserRole, List[ActionEventValue]] =
      Map(
        UserRoles.Pro -> List(COMMENT),
        UserRoles.Admin -> List(COMMENT, CONSUMER_ATTACHMENTS, PROFESSIONAL_ATTACHMENTS),
        UserRoles.DGCCRF -> List(COMMENT, CONTROL)
      )

    def fromValue(value: String) = actionEvents.find(_.value == value).getOrElse(ActionEventValue(""))
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

    val METROPOLE = AURA ++ BretagneFrancheComte ++ Bretagne ++ CDVL ++ Corse ++ GrandEst ++ HautsDeFrance ++
      IleDeFrance ++ Normandie ++ NouvelleAquitaine ++ OCC ++ PaysDeLaLoire ++ ProvenceAlpesCoteAzur
    val DOM_TOM = CollectivitesOutreMer ++ Guadeloupe ++ Guyane ++ LaReunion ++ Martinique ++ Mayotte

    val ALL = METROPOLE ++ DOM_TOM

    def fromPostalCode(postalCode: String) = {
      postalCode match {
        case "97150" => Some("978")
        case "97133" => Some("977")
        case code if code.startsWith("200") => Some("2A")
        case code if code.startsWith("201") => Some("2A")
        case code if code.startsWith("202") => Some("2B")
        case code => Departments.ALL.find(postalCode.startsWith(_))
      }
    }
  }

}
