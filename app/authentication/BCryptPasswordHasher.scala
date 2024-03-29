package authentication

import authentication.BCryptPasswordHasher._
import org.mindrot.jbcrypt.BCrypt

/** Implementation of the password hasher based on BCrypt.
  *
  * @param logRounds
  *   The log2 of the number of rounds of hashing to apply.
  * @see
  *   [[http://www.mindrot.org/files/jBCrypt/jBCrypt-0.2-doc/BCrypt.html#gensalt(int) gensalt]]
  */
class BCryptPasswordHasher(logRounds: Int = 10) extends PasswordHasher {

  /** Gets the ID of the hasher.
    *
    * @return
    *   The ID of the hasher.
    */
  override def id = ID

  /** Hashes a password.
    *
    * This implementation does not return the salt separately because it is embedded in the hashed password. Other
    * implementations might need to return it so it gets saved in the backing store.
    *
    * @param plainPassword
    *   The password to hash.
    * @return
    *   A PasswordInfo containing the hashed password.
    */
  override def hash(plainPassword: String) = PasswordInfo(
    hasher = id,
    password = BCrypt.hashpw(plainPassword, BCrypt.gensalt(logRounds))
  )

  /** Checks if a password matches the hashed version.
    *
    * @param passwordInfo
    *   The password retrieved from the backing store.
    * @param suppliedPassword
    *   The password supplied by the user trying to log in.
    * @return
    *   True if the password matches, false otherwise.
    */
  override def matches(passwordInfo: PasswordInfo, suppliedPassword: String) =
    BCrypt.checkpw(suppliedPassword, passwordInfo.password)

  /** Indicates if a password info hashed with this hasher is deprecated.
    *
    * In case of the BCrypt password hasher, a password is deprecated if the log rounds have changed.
    *
    * @param passwordInfo
    *   The password info to check the deprecation status for.
    * @return
    *   True if the given password info is deprecated, false otherwise. If a hasher isn't suitable for the given
    *   password, this method should return None.
    */
  override def isDeprecated(passwordInfo: PasswordInfo): Option[Boolean] =
    Option(isSuitable(passwordInfo)).collect { case true =>
      val LogRoundsPattern(lr) = passwordInfo.password
      // Is deprecated if the log rounds has changed
      lr != logRounds.toString
    }
}

/** The companion object.
  */
object BCryptPasswordHasher {

  /** The ID of the hasher.
    */
  val ID = "bcrypt"

  /** The pattern to extract the log rounds from the password string.
    */
  val LogRoundsPattern = """^\$\w{2}\$(\d{1,2})\$.+""".r
}
