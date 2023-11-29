package utils

import models.User
import play.api.test.FakeRequest
import utils.auth.BCryptPasswordHasher
import utils.auth.CookieAuthenticator

object AuthHelpers {
  implicit class FakeRequestOps[A](request: FakeRequest[A]) {
    def withAuthCookie(userEmail: EmailAddress, cookieAuthenticator: CookieAuthenticator): FakeRequest[A] = {
      val cookie = cookieAuthenticator.init(userEmail)(request).toOption.get
      request.withCookies(cookie)
    }
  }

  val hasher = new BCryptPasswordHasher()

  def encryptUser(user: User): User =
    user.copy(password = hasher.hash(user.password).password)
}
