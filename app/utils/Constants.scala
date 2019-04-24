package utils

object Constants extends App {

  object StatusPro {

    // Valeurs possibles de status_pro
    sealed case class StatusProValues(value: String)

    object A_TRAITER extends StatusProValues("A_TRAITER")
    object NA extends StatusProValues("NA")
    object TRAITEMENT_EN_COURS extends StatusProValues("TRAITEMENT_EN_COURS")
    object A_RAPPELER extends StatusProValues("A_RAPPELER")
    object A_ENVOYER_EMAIL extends StatusProValues("A_ENVOYER_EMAIL")
    object A_ENVOYER_COURRIER extends StatusProValues("A_ENVOYER_COURRIER")
    object ATTENTE_REPONSE extends StatusProValues("ATTENTE_REPONSE")
    object A_TRANSFERER_SIGNALEMENT extends StatusProValues("A_TRANSFERER_SIGNALEMENT")
    object SIGNALEMENT_TRANSMIS extends StatusProValues("SIGNALEMENT_TRANSMIS")
    object SIGNALEMENT_REFUSE extends StatusProValues("SIGNALEMENT_REFUSE")
    object PROMESSE_ACTION extends StatusProValues("PROMESSE_ACTION")

  }

  object Event {
    // Valeurs possibles de event_type
    sealed case class EventTypeValues(value: String)

    object PRO extends EventTypeValues("PRO")
    object CONSO extends EventTypeValues("CONSO")
    object DGCCRF extends EventTypeValues("DGCCRF")

    // Valeurs possibles de result_action de la table Event
    sealed case class ResultActionProValues(value: String)

    object OK extends ResultActionProValues("OK")
    object KO extends ResultActionProValues("KO")

  }

  object EventPro {

    // Valeurs possibles de action de la table Event
    sealed case class ActionProValues(value: String)

    object A_CONTACTER extends ActionProValues("A_CONTACTER")
    object HORS_PERIMETRE extends ActionProValues("HORS_PERIMETRE")
    object CONTACT_TEL extends ActionProValues("CONTACT_TEL")
    object CONTACT_EMAIL extends ActionProValues("CONTACT_EMAIL")
    object CONTACT_COURRIER extends ActionProValues("CONTACT_COURRIER")
    object REPONSE_PRO_CONTACT extends ActionProValues("REPONSE_PRO_CONTACT")
    object ENVOI_SIGNALEMENT extends ActionProValues("ENVOI_SIGNALEMENT")
    object REPONSE_PRO_SIGNALEMENT extends ActionProValues("REPONSE_PRO_SIGNALEMENT")

  }


  println("Status pro " + Constants.StatusPro.A_ENVOYER_COURRIER.value)
  println("Event " + Constants.EventPro.ENVOI_SIGNALEMENT)
}
