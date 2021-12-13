package utils.silhouette.responseconso

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService

import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ReponseConsoApiKeyService @Inject() (implicit val executionContext: ExecutionContext)
    extends IdentityService[ReponseConsoAPIKey] {

  def retrieve(loginInfo: LoginInfo): Future[Option[ReponseConsoAPIKey]] =
    Future(Some(ReponseConsoAPIKey()))
}
