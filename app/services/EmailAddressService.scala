package services

object EmailAddressService {

  private val adminEmailRegexp = """.+\.betagouv(\+.+)?@gmail\.com|.+@beta\.gouv\.fr""".r
  private val dgccrfEmailRegexp = """.+\.gouv\.fr""".r

  def isEmailAcceptableForAdminAccount(emailAddress: String): Boolean =
    adminEmailRegexp.matches(emailAddress)

  def isEmailAcceptableForDgccrfAccount(emailAddress: String): Boolean =
    dgccrfEmailRegexp.matches(emailAddress)

}
