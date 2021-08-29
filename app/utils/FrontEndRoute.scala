package utils

import play.api.Configuration
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FrontEndRoute @Inject() (config: Configuration) {

  private[this] val baseUrl = config.get[URI]("play.website.url")

}
