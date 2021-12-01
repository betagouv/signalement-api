import models.website.WebsiteKind

import play.api.mvc.QueryStringBindable

package object controllers {

  implicit val WebsiteKindQueryStringBindable: QueryStringBindable[WebsiteKind] =
    QueryStringBindable.bindableString
      .transform[WebsiteKind](
        kinds => WebsiteKind.fromValue(kinds),
        websiteKinds => websiteKinds.value
      )
}
