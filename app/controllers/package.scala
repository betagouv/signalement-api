import models.WebsiteKind
import play.api.mvc.QueryStringBindable

package object controllers {

  implicit val WebsiteKindQueryStringBindable: QueryStringBindable[Seq[WebsiteKind]] =
    QueryStringBindable.bindableString
      .transform[Seq[WebsiteKind]](
        kinds => kinds.split(",").toSeq.map(WebsiteKind.fromValue),
        websiteKinds => websiteKinds.mkString(",")
      )
}
