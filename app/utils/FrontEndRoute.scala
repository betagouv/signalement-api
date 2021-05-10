package utils

import java.net.URI

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class FrontEndRoute @Inject()(config: Configuration) {

  private[this] val baseUrl = config.get[URI]("play.website.url")

}

