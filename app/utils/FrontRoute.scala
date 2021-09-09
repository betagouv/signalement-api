package utils

import models.AuthToken
import play.api.Configuration

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrontRoute @Inject() (config: Configuration) {

  object website {
    val url = config.get[URI]("play.website.url")
    def litige = url.resolve(s"/litige")
  }

  object dashboard {
    val url = config.get[URI]("play.dashboard.url")
    def login = new URI(url.toString + "/connexion")
    def registerDgccrf(token: String) = new URI(url.toString + s"/dgccrf/rejoindre/?token=$token")
    def registerPro(siret: SIRET, token: String) = new URI(
      url.toString + s"/entreprise/rejoindre/${siret}?token=${token}"
    )
    def validateEmail(token: String) = new URI(url.toString + s"/connexion/validation-email?token=${token}")
    def reportReview(id: String) = new URI(url.toString + s"/suivi-des-signalements/$id/avis")
    def resetPassword(authToken: AuthToken) = new URI(url.toString + s"/connexion/nouveau-mot-de-passe/${authToken.id}")
    def activation = new URI(url.toString + "/activation")
  }
}
