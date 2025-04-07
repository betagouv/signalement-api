package services

object EmailAddressService {

  private val adminEmailRegexp  = """.+\.betagouv(\+.+)?@gmail\.com|.+@beta\.gouv\.fr|.+@dgccrf\.finances\.gouv\.fr""".r
  private val dgccrfEmailRegexp = """.+\.gouv\.fr""".r
  private val dgalEmailRegexp   = """.+\.gouv\.fr""".r

  def isEmailAcceptableForAdminAccount(emailAddress: String): Boolean =
    adminEmailRegexp.matches(emailAddress)

  def isEmailAcceptableForDgccrfAccount(emailAddress: String): Boolean =
    dgccrfEmailRegexp.matches(emailAddress)

  def isEmailAcceptableForDgalAccount(emailAddress: String): Boolean =
    dgalEmailRegexp.matches(emailAddress)

  def isAgentEmail(email: String) =
    isEmailAcceptableForDgalAccount(email) || isEmailAcceptableForDgccrfAccount(email)

}
