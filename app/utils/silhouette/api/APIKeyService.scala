package utils.silhouette.api

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import repositories.ConsumerRepository

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ApiKeyService @Inject() (consumerRepository: ConsumerRepository)(implicit val executionContext: ExecutionContext)
    extends IdentityService[APIKey] {

  def retrieve(loginInfo: LoginInfo): Future[Option[APIKey]] = consumerRepository
    .find(UUID.fromString(loginInfo.providerKey))
    .map(_.map(c => APIKey(c.id)))

}
