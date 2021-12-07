import models.ReportResponseType

import models.website.WebsiteKind
import play.api.mvc.QueryStringBindable

package object controllers {

  implicit val WebsiteKindQueryStringBindable: QueryStringBindable[WebsiteKind] =
    QueryStringBindable.bindableString
      .transform[WebsiteKind](
        kinds => WebsiteKind.fromValue(kinds),
        websiteKinds => websiteKinds.value
      )

  implicit val ReportResponseTypeQueryStringBindable: QueryStringBindable[ReportResponseType] =
    QueryStringBindable.bindableString
      .transform[ReportResponseType](
        reportResponseType => ReportResponseType.withName(reportResponseType),
        reportResponseType => reportResponseType.entryName
      )
}
