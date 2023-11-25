package utils.silhouette.auth

import com.mohiva.play.silhouette.api.Authorization
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator
import models.User
import models.UserPermission
import models.UserRole
import play.api.mvc.Request

import scala.concurrent.Future

//case class WithPermission(anyOfPermissions: UserPermission.Value*) extends Authorization[User, CookieAuthenticator] {
//  override def isAuthorized[A](user: User, authenticator: CookieAuthenticator)(implicit r: Request[A]) =
//    Future.successful {
//      WithPermission.isAuthorized(user, anyOfPermissions: _*)
//    }
//}
//
//object WithPermission {
//  def isAuthorized(user: User, anyOfPermissions: UserPermission.Value*): Boolean =
//    anyOfPermissions.intersect(user.userRole.permissions).nonEmpty
//}
//
//case class WithRole(anyOfRoles: UserRole*) extends Authorization[User, CookieAuthenticator] {
//  override def isAuthorized[A](user: User, authenticator: CookieAuthenticator)(implicit r: Request[A]) =
//    Future.successful {
//      WithRole.isAuthorized(user, anyOfRoles: _*)
//    }
//}
//
//object WithRole {
//  def isAuthorized(user: User, anyOfRoles: UserRole*): Boolean =
//    anyOfRoles.contains(user.userRole)
//}
