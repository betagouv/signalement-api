package utils

object Constants extends App {

  object StatusPro {

    // Valeurs possibles de status_pro
    sealed case class StatusProValues(val value: String)

    object A_TRAITER extends StatusProValues("A TRAITER")
    object NA extends StatusProValues("NA")
    object TRAITEMENT_EN_COURS extends StatusProValues("TRAITEMENT EN COURS")
    object A_RAPPELER extends StatusProValues("A RAPPELER")
    object A_ENVOYER_EMAIL extends StatusProValues("A ENVOYER EMAIL")
    object A_ENVOYER_COURRIER extends StatusProValues("A ENVOYER COURRIER")
    object ATTENTE_REPONSE extends StatusProValues("ATTENTE REPONSE")
    object A_TRANSFERER_SIGNALEMENT extends StatusProValues("A TRANSFERER SIGNALEMENT")
    object SIGNALEMENT_TRANSMIS extends StatusProValues("SIGNALEMENT TRANSMIS")
    object SIGNALEMENT_REFUSE extends StatusProValues("SIGNALEMENT REFUSE")
    object PROMESSE_ACTION extends StatusProValues("PROMESSE ACTION")

    def fromString(value: String) = value match {
      case "A_TRAITER" => Some(A_TRAITER)
      case "NA" => Some(NA)
      case "TRAITEMENT EN COURS" => Some(TRAITEMENT_EN_COURS)
      case "A RAPPELER" => Some(A_RAPPELER)
      case "A ENVOYER EMAIL" => Some(A_ENVOYER_EMAIL)
      case "A ENVOYER COURRIER" => Some(A_ENVOYER_COURRIER)
      case "ATTENTE REPONSE" => Some(ATTENTE_REPONSE)
      case "A TRANSFERER SIGNALEMENT" => Some(A_TRANSFERER_SIGNALEMENT)
      case "SIGNALEMENT TRANSMIS" => Some(SIGNALEMENT_TRANSMIS)
      case "SIGNALEMENT REFUSE" => Some(SIGNALEMENT_REFUSE)
      case "PROMESSE ACTION" => Some(PROMESSE_ACTION)
    }

  }


  object StatusConso {

    // Valeurs possibles de action de la table Event
    sealed case class StatusConsoValues(value: String)

    object VIDE extends StatusConsoValues("")
    object A_RECONTACTER extends StatusConsoValues("A RECONTACTER")
    object A_INFORMER_TRANSMISSION extends StatusConsoValues("A INFORMER TRANSMISSION")
    object A_INFORMER_REPONSE_PRO extends StatusConsoValues("A INFORMER REPONSE PRO")

    def fromString(value: String) = value match {
      case "" => Some(VIDE)
      case "A RECONTACTER" => Some(A_RECONTACTER)
      case "A INFORMER TRANSMISSION" => Some(A_INFORMER_TRANSMISSION)
      case "A INFORMER REPONSE PRO" => Some(A_INFORMER_REPONSE_PRO)

    }
  }


  object EventType {

    // Valeurs possibles de event_type
    sealed case class EventTypeValues(value: String)

    object PRO extends EventTypeValues("PRO")
    object CONSO extends EventTypeValues("CONSO")
    object DGCCRF extends EventTypeValues("DGCCRF")


    // Valeurs possibles de result_action de la table Event
    sealed case class ResultActionProValues(value: String)

    object OK extends ResultActionProValues("OK")
    object KO extends ResultActionProValues("KO")

    def fromString(value: String) = value match {
      case "PRO" => Some(PRO)
      case "CONSO" => Some(CONSO)
      case "DGCCRF" => Some(DGCCRF)
      case _ => None

    }

  }

  case class ActionEvent(value: String)

  object EventPro {

    // Valeurs possibles de action de la table Event
    sealed case class ActionProValues(override val value: String) extends ActionEvent(value)

    object A_CONTACTER extends ActionProValues("A CONTACTER")
    object HORS_PERIMETRE extends ActionProValues("HORS PERIMETRE")
    object CONTACT_TEL extends ActionProValues("CONTACT TEL")
    object CONTACT_EMAIL extends ActionProValues("CONTACT EMAIL")
    object CONTACT_COURRIER extends ActionProValues("CONTACT COURRIER")
    object REPONSE_PRO_CONTACT extends ActionProValues("REPONSE PRO CONTACT")
    object ENVOI_SIGNALEMENT extends ActionProValues("ENVOI SIGNALEMENT")
    object REPONSE_PRO_SIGNALEMENT extends ActionProValues("REPONSE PRO SIGNALEMENT")

    def fromString(value: String) = value match {
      case "A CONTACTER" => Some(A_CONTACTER)
      case "HORS PERIMETRE" => Some(HORS_PERIMETRE)
      case "CONTACT TEL" => Some(CONTACT_TEL)
      case "CONTACT EMAIL" => Some(CONTACT_EMAIL)
      case "CONTACT COURRIER" => Some(CONTACT_COURRIER)
      case "REPONSE PRO CONTACT" => Some(REPONSE_PRO_CONTACT)
      case "ENVOI SIGNALEMENT" => Some(ENVOI_SIGNALEMENT)
      case "REPONSE PRO SIGNALEMENT" => Some(REPONSE_PRO_SIGNALEMENT)

    }
  }

  object EventConso {

    // Valeurs possibles de action de la table Event
    sealed case class ActionConsoValues(override val value: String) extends ActionEvent(value)

    object VIDE extends ActionConsoValues("")
    object CONSO_CONTACTE extends ActionConsoValues("CONSO CONTACTE")
    object CONSO_INFORME_TRANSMISSION extends ActionConsoValues("CONSO INFORME TRANSMISSION")
    object CONSO_INFORME_REPONSE_PRO extends ActionConsoValues("CONSO INFORME REPONSE PRO")

    def fromString(value: String) = value match {
      case "" => Some(VIDE)
      case "CONSO CONTACTE" => Some(CONSO_CONTACTE)
      case "CONSO INFORME TRANSMISSION" => Some(CONSO_INFORME_TRANSMISSION)
      case "CONSO INFORME REPONSE PRO" => Some(CONSO_INFORME_REPONSE_PRO)

    }
  }




  println("Status pro " + Constants.StatusPro.A_ENVOYER_COURRIER.value)
  println("Event " + Constants.EventPro.ENVOI_SIGNALEMENT)

  val value1: Option[String] = Some("toto")
  val value2: Option[String] = None

  println(s"value1 $value1")
  println(s"value2 $value2")

}
