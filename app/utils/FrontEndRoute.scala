package utils

import java.net.URI

object FrontEndRoute {
  val baseUrl = configuration.get[URI]("play.website.url")
  def emailConfirmed(email: String) = s"/"
}
