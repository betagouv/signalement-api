package utils.silhouette

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.JWTAuthenticator
import models.User

trait AuthEnv extends Env {
  type I = User
  type A = JWTAuthenticator
}

object Login {
  val localProvider = "signalconso"
}

