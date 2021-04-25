package utils.silhouette

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.PasswordInfo
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.password.BCryptPasswordHasher

object Implicits {
  implicit def key2loginInfo(key: String): LoginInfo = LoginInfo(CredentialsProvider.ID, key)
  implicit def loginInfo2key(loginInfo: LoginInfo): String = loginInfo.providerKey
  implicit def pwd2passwordInfo(pwd: String): PasswordInfo = PasswordInfo(BCryptPasswordHasher.ID, pwd, salt = Some("$2a$10$llw0G6IyibUob8h5XRt9xuRczaGdCm/AiV6SSjf5v78XS824EGbh"))
  implicit def passwordInfo2pwd(passwordInfo: PasswordInfo): String = passwordInfo.password
}
