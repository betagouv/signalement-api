package models.report.sampledata

import models.AuthProvider.SignalConso
import models.User
import models.UserRole
import models.UserRole.Professionnel
import utils.EmailAddress

import java.time.OffsetDateTime
import java.util.UUID

object ProUserGenerator {

  val proUserA = buildProUser(
    UUID.fromString("8a87f4f4-185a-47c4-a71d-7e27577d7483"),
    "André",
    "Andlard",
    "dev.signalconso+SAMPLE_PRO1@gmail.com",
    Professionnel
  )
  val proUserB = buildProUser(
    UUID.fromString("0920b263-223f-40ae-a5a1-9efe5b624966"),
    "Brigitte",
    "Briguenier",
    "dev.signalconso+SAMPLE_PRO2@gmail.com",
    Professionnel
  )
  val proUserC = buildProUser(
    UUID.fromString("3f80c538-676b-4f2b-a318-7625379e0040"),
    "Camille",
    "Camion",
    "dev.signalconso+SAMPLE_PRO3@gmail.com",
    Professionnel
  )

  val proUserD = buildProUser(
    UUID.fromString("6a17610b-2fb5-4d0e-b4b6-a700d1b446a7"),
    "Damien",
    "Damont",
    "dev.signalconso+SAMPLE_PRO4@gmail.com",
    Professionnel
  )

  val proUserE = buildProUser(
    UUID.fromString("b5dead94-d3ee-4718-a181-97dcb6c5b867"),
    "Élodie",
    "Elovirard",
    "dev.signalconso+SAMPLE_PRO5@gmail.com",
    Professionnel
  )

  val proUserF = buildProUser(
    UUID.fromString("d91520ec-b1d6-4163-9f70-ebd9117f06bc"),
    "François",
    "Français",
    "dev.signalconso+SAMPLE_PRO6@gmail.com",
    Professionnel
  )

  val proUserJohnny = buildProUser(
    UUID.fromString("9110761e-34b5-48b5-af76-e5beee6291b0"),
    "Johnny",
    "Jovial",
    "dev.signalconso+SAMPLE_PRO7@gmail.com",
    Professionnel
  )

  private def buildProUser(
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
