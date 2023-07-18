package repositories.companyactivationattempt

import models.company.CompanyActivationAttempt
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future
import scala.concurrent.duration.Duration
trait CompanyActivationAttemptRepositoryInterface extends CRUDRepositoryInterface[CompanyActivationAttempt] {

  def countAttempts(siret: String, delay: Duration): Future[Int]

  def listAttempts(siret: String): Future[Seq[CompanyActivationAttempt]]
}
