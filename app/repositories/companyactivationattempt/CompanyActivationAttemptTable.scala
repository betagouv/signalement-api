package repositories.companyactivationattempt

import models.company.CompanyActivationAttempt
import repositories.DatabaseTable
import repositories.PostgresProfile.api._

import java.time.OffsetDateTime

class CompanyActivationAttemptTable(tag: Tag)
    extends DatabaseTable[CompanyActivationAttempt](tag, "company_activation_attempts") {

  def siret = column[String]("siret")
  def timestamp = column[OffsetDateTime]("timestamp")

  def * = (id, siret, timestamp) <> ((CompanyActivationAttempt.apply _).tupled, CompanyActivationAttempt.unapply)
}

object CompanyActivationAttemptTable {
  val table = TableQuery[CompanyActivationAttemptTable]
}
