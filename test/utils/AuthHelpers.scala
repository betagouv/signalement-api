package utils

import play.api.test.FakeRequest
import utils.auth.CookieAuthenticator

object AuthHelpers {
  implicit class FakeRequestOps[A](request: FakeRequest[A]) {
    def withAuthCookie(userEmail: EmailAddress, cookieAuthenticator: CookieAuthenticator): FakeRequest[A] = {
      val cookie = cookieAuthenticator.init(userEmail)(request).toOption.get
      request.withCookies(cookie)
    }
  }
}
