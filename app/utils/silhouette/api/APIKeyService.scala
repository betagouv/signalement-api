package utils.silhouette.api

import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

class ApiKeyService @Inject()(implicit val executionContext: ExecutionContext) extends IdentityService[APIKey] {

  def retrieve(loginInfo: LoginInfo): Future[Option[APIKey]] = {
    Future(Some(APIKey()))
  }
}