package utils

object EmailSubjects {
  val RESET_PASSWORD = "Votre mot de passe SignalConso"
  val NEW_COMPANY_ACCESS = (companyName: String) =>
    s"Vous avez maintenant accès à l'entreprise ${companyName} sur SignalConso"
  val COMPANY_ACCESS_INVITATION = (companyName: String) => s"Rejoignez l'entreprise ${companyName} sur SignalConso"
  val DGCCRF_ACCESS_LINK        = "Votre accès DGCCRF sur SignalConso"
  val ADMIN_ACCESS_LINK         = "Votre accès Administrateur sur SignalConso"
  val VALIDATE_EMAIL            = "Veuillez valider cette adresse email"
  val NEW_REPORT                = "Nouveau signalement"
  val REPORT_REOPENING          = "Réouverture signalement en attente de réponse"
  val REPORT_ACK_PRO            = "Votre réponse au signalement"
  val REPORT_ACK_PRO_ON_ADMIN_COMPLETION = "Signalement résolu"
  val REPORT_UNREAD_REMINDER             = "Nouveau(x) signalement(s)"
  val REPORT_TRANSMITTED_REMINDER        = "Signalement(s) en attente de réponse"
  val REPORT_NOTIF_DGCCRF = (cnt: Int, additional: Option[String]) =>
    s"[SignalConso] ${additional.getOrElse("")}${if (cnt > 1) s"${cnt} nouveaux signalements"
      else "Un nouveau signalement"}"
  val INACTIVE_DGCCRF_ACCOUNT_REMINDER = "Votre compte SignalConso est inactif"
  val PRO_NEW_COMPANIES_ACCESSES = (siren: SIREN) => s"Vous avez maintenant accès à l'entreprise $siren sur SignalConso"
  val PRO_COMPANIES_ACCESSES_INVITATIONS = (siren: SIREN) => s"Rejoignez l'entreprise $siren sur SignalConso"
}
