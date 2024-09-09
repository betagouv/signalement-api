package controllers

import models.User
import models.UserRole.Professionnel
import orchestrators.ReportWithData
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.twirl.api.Html
import utils.Constants.EventType
import utils.FrontRoute

import java.util.Locale
import scala.tools.nsc.tasty.SafeEq

class HtmlFromTemplateGenerator(messagesApi: MessagesApi, frontRoute: FrontRoute) {

  def reportPdf(reportData: ReportWithData, user: User): Html = {

    val lang                               = Lang(reportData.report.lang.getOrElse(Locale.FRENCH))
    val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    if (user.userRole == Professionnel) {
      views.html.pdfs
        .reportPro(
          reportData.report,
          reportData.maybeCompany,
          reportData.events,
          reportData.responseOption,
          reportData.companyEvents.filter(_._1.eventType === EventType.PRO)
        )(frontRoute = frontRoute, None, messagesProvider)
    } else {
      views.html.pdfs
        .report(
          reportData.report,
          reportData.maybeCompany,
          reportData.events,
          reportData.responseOption,
          reportData.consumerReviewOption,
          reportData.engagementReviewOption,
          reportData.companyEvents,
          reportData.files
        )(frontRoute = frontRoute, None, messagesProvider)
    }

  }

}
