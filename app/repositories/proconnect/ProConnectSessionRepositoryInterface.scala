package repositories.proconnect

import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

trait ProConnectSessionRepositoryInterface extends CRUDRepositoryInterface[ProConnectSession] {

  def find(state: String): Future[Option[ProConnectSession]]

  def delete(state: String): Future[Int]

}
