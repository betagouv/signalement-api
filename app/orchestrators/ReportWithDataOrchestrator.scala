package orchestrators

import models.User
import models.company.Company
import models.event.Event
import models.report.Report
import models.report.ReportFile
import models.report.ReportResponse
import models.report.review.ResponseConsumerReview

case class ReportWithData(
    report: Report,
    maybeCompany: Option[Company],
    events: Seq[(Event, Option[User])],
    responseOption: Option[ReportResponse],
    consumerReviewOption: Option[ResponseConsumerReview],
    companyEvents: Seq[(Event, Option[User])],
    files: Seq[ReportFile]
)
