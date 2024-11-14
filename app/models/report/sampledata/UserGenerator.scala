package models.report.sampledata

import models.AuthProvider.SignalConso
import models.User
import models.UserRole
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

object UserGenerator {

  def generateSampleUser(
      id: UUID,
      firstName: String,
      lastName: String,
      email: String,
      userRole: UserRole
  ): User =
    User(
      id = id,
      password = "",
      email = EmailAddress(email),
      firstName = firstName,
      lastName = lastName,
      userRole = userRole,
      lastEmailValidation = Some(OffsetDateTime.now()),
      deletionDate = None,
      authProvider = SignalConso,
      authProviderId = None
    )

}
