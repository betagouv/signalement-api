package models.auth

import models.User
import play.api.mvc.Cookie

case class UserSession(cookie: Cookie, user: User)
