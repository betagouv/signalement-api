package utils

import config.SignalConsoConfiguration
import models.auth.AuthToken
import models.report.review.ResponseEvaluation

import java.net.URI
import java.util.UUID

class FrontRoute(signalConsoConfiguration: SignalConsoConfiguration) {

  object website {
    val url    = signalConsoConfiguration.websiteURL
    def litige = url.resolve(s"/litige")

    def reportReview(id: String)(evaluation: ResponseEvaluation) = url.resolve(
      s"/avis/$id?evaluation=${evaluation.entryName}"
    )
  }

  object dashboard {
    def url(path: String) = new URI(signalConsoConfiguration.dashboardURL.toString + path)
    def login             = url("/connexion")
    def subscriptionDGCCRFReport(reportId: UUID) = url(
      s"/suivi-des-signalements/report/${reportId.toString}?mtm_campaign=subscription&anchor=attachment"
    )

    def validateEmail(token: String)        = url(s"/connexion/validation-email?token=${token}")
    def resetPassword(authToken: AuthToken) = url(s"/connexion/nouveau-mot-de-passe/${authToken.id}")
    def activation                          = url("/activation")
    def welcome                             = url("/")

    object Admin {
      def register(token: String) = url(s"/admin/rejoindre/?token=$token")
    }
    object Agent {
      def register(token: String) = url(s"/agent/rejoindre/?token=$token")
    }
    object Pro {
      def subscriptionDGCCRFCompanySummary(companyId: UUID) = url(
        s"/bilan-entreprise/${companyId.toString}?mtm_campaign=subscription"
      )
      def register(siret: SIRET, token: String) = url(s"/entreprise/rejoindre/${siret}?token=${token}")
      def manageNotification()                  = url(s"/mes-entreprises")
    }
  }
}
