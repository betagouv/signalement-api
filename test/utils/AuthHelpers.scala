package utils

import authentication.BCryptPasswordHasher
import authentication.CookieAuthenticator
import models.User
import play.api.test.FakeRequest

object AuthHelpers {
  implicit class FakeRequestOps[A](request: FakeRequest[A]) {
    def withAuthCookie(userEmail: EmailAddress, cookieAuthenticator: CookieAuthenticator): FakeRequest[A] = {
      val cookie = cookieAuthenticator.initSignalConsoCookie(userEmail, None).toOption.get
      request.withCookies(cookie)
    }
  }

  val hasher = new BCryptPasswordHasher()

  def encryptUser(user: User): User =
    user.copy(password = hasher.hash(user.password).password)
}
