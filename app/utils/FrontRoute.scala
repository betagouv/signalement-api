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
    def url(path: String) = new URI(config.get[URI]("play.dashboard.url").toString + path)
    def login = url("/connexion")
    def validateEmail(token: String) = url(s"/connexion/validation-email?token=${token}")
    def reportReview(id: String) = url(s"/suivi-des-signalements/$id/avis")
    def resetPassword(authToken: AuthToken) = url(s"/connexion/nouveau-mot-de-passe/${authToken.id}")
    def activation = url("/activation")
    object Dgccrf {
      def register(token: String) = url(s"/dgccrf/rejoindre/?token=$token")
    }
    object Pro {
      def register(siret: SIRET, token: String) = url(s"/entreprise/rejoindre/${siret}?token=${token}")
      def manageNotification() = url(s"/mes-entreprises")
    }
  }
}
