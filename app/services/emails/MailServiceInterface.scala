package services.emails

import scala.concurrent.Future

trait MailServiceInterface {
  def send(email: BaseEmail): Future[Unit]
}
