package controllers

import orchestrators.ReportWithData
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.i18n.MessagesImpl
import play.api.i18n.MessagesProvider
import play.twirl.api.Html
import utils.FrontRoute

import java.util.Locale

class HtmlFromTemplateGenerator(messagesApi: MessagesApi, frontRoute: FrontRoute) {

  def reportPdf(reportData: ReportWithData): Html = {

    val lang                               = Lang(reportData.report.lang.getOrElse(Locale.FRENCH))
    val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

    views.html.pdfs
      .report(
        reportData.report,
        reportData.maybeCompany,
        reportData.events,
        reportData.responseOption,
        reportData.consumerReviewOption,
        reportData.companyEvents,
        reportData.files
      )(frontRoute = frontRoute, None, messagesProvider)
  }
}
