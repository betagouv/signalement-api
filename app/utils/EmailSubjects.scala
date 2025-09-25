package utils

object EmailSubjects {
  val RESET_PASSWORD = "Votre mot de passe SignalConso"
  val NEW_COMPANY_ACCESS = (companyName: String) =>
    s"Vous avez maintenant accès à l'entreprise ${companyName} sur SignalConso"
  val COMPANY_ACCESS_INVITATION = (companyName: String) => s"Rejoignez l'entreprise ${companyName} sur SignalConso"
  val DGCCRF_ACCESS_LINK        = "Votre accès DGCCRF sur SignalConso"
  val ADMIN_ACCESS_LINK         = "Votre accès Administrateur sur SignalConso"
  val ADMIN_PROBE_TRIGGERED     = "Sonde Signal conso déclenchée"
  val VALIDATE_EMAIL            = "Veuillez valider cette adresse email"
  val NEW_REPORT                = "Nouveau signalement"
  val REPORT_REOPENING          = "Réouverture signalement en attente de réponse"
  val REPORT_ACK_PRO            = "Votre réponse au signalement"
  val REPORT_ACK_PRO_ON_ADMIN_COMPLETION = "Signalement résolu"
  val REPORT_ASSIGNED                    = "Signalement affecté"
  val REPORT_UNREAD_REMINDER             = "Nouveau(x) signalement(s)"
  val REPORT_TRANSMITTED_REMINDER        = "Signalement(s) en attente de réponse"
  val REPORT_LAST_CHANCE_REMINDER        = "Signalement(s) expirant demain"
  val REPORT_NOTIF_DGCCRF = (count: Int, additional: String) =>
    s"[SignalConso] $additional${if (count > 1) s"$count nouveaux signalements ont été déposés"
      else "Un nouveau signalement a été déposé"}"
  val INACTIVE_DGCCRF_ACCOUNT_REMINDER = "Votre compte SignalConso est inactif"
  val PRO_NEW_COMPANIES_ACCESSES = (siren: SIREN) => s"Vous avez maintenant accès à l'entreprise $siren sur SignalConso"
  val PRO_COMPANIES_ACCESSES_INVITATIONS = (siren: SIREN) => s"Rejoignez l'entreprise $siren sur SignalConso"
  val UPDATE_EMAIL_ADDRESS               = "Validez votre nouvelle adresse email SignalConso"
}
