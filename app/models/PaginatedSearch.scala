package models

import play.api.data.Form
import play.api.data.Forms._

case class PaginatedSearch(
    offset: Option[Int] = Some(0),
    limit: Option[Int] = None
)

object PaginatedSearch {
  val form = Form(

  )
}