package authentication

object Credentials {
  def toPasswordInfo(pwd: String): PasswordInfo =
    PasswordInfo(BCryptPasswordHasher.ID, pwd, salt = Some("SignalConso"))
}
