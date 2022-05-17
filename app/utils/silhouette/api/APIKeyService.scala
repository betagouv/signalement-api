package utils.silhouette.api

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import repositories.consumer.ConsumerRepositoryInterface

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ApiKeyService @Inject() (consumerRepository: ConsumerRepositoryInterface)(implicit
    val executionContext: ExecutionContext
) extends IdentityService[APIKey] {

  def retrieve(loginInfo: LoginInfo): Future[Option[APIKey]] = {
    println(s"------------------ loginInfo = ${loginInfo} ------------------")
    consumerRepository
      .get(UUID.fromString(loginInfo.providerKey))
      .map(_.map(c => APIKey(c.id)))
      .map { e =>
        println(s"------------------ e = ${e} ------------------")
        e
      }
  }

}
