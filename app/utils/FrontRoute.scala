package utils

import config.SignalConsoConfiguration
import models.auth.AuthToken
import models.report.review.ResponseEvaluation

import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrontRoute @Inject(signalConsoConfiguration: SignalConsoConfiguration) {

  object website {
    val url = signalConsoConfiguration.websiteURL
    def litige = url.resolve(s"/litige")
  }

  object dashboard {
    def url(path: String) = new URI(signalConsoConfiguration.dashboardURL.toString + path)
    def login = url("/connexion")
    def validateEmail(token: String) = url(s"/connexion/validation-email?token=${token}")
    def reportReview(id: String)(evaluation: ResponseEvaluation) = url(
      s"/suivi-des-signalements/$id/avis?evaluation=${evaluation.entryName}"
    )
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
