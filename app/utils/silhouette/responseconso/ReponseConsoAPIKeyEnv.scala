package utils.silhouette.responseconso

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.DummyAuthenticator

trait ReponseConsoAPIKeyEnv extends Env {
  type I = ReponseConsoAPIKey
  type A = DummyAuthenticator
}
