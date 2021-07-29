package utils.silhouette.api

import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.Identity
import com.mohiva.play.silhouette.api.LoginInfo
import javax.inject.Inject

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

class ApiKeyService @Inject() (implicit val executionContext: ExecutionContext) extends IdentityService[APIKey] {

  def retrieve(loginInfo: LoginInfo): Future[Option[APIKey]] =
    Future(Some(APIKey()))
}
