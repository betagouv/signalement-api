package utils.silhouette

import utils.auth.BCryptPasswordHasher
import utils.auth.PasswordInfo

object Credentials {
  def toPasswordInfo(pwd: String): PasswordInfo =
    PasswordInfo(BCryptPasswordHasher.ID, pwd, salt = Some("SignalConso"))
}
