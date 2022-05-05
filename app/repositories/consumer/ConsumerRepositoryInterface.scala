package repositories.consumer
import com.google.inject.ImplementedBy
import models.Consumer
import repositories.CRUDRepositoryInterface

import scala.concurrent.Future

@ImplementedBy(classOf[ConsumerRepository])
trait ConsumerRepositoryInterface extends CRUDRepositoryInterface[Consumer] {
  def getAll(): Future[Seq[Consumer]]
}
