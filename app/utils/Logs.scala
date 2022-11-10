package utils

import play.api.MarkerContext

object Logs {
  // Adds a structured title that can be parsed easily in New Relic
  implicit class RichLogger(logger: play.api.Logger) {

    def infoWithTitle(title: => String, message: => String)(implicit mc: MarkerContext) =
      logger.info(s"[$title] $message")

    def warnWithTitle(title: => String, message: => String)(implicit mc: MarkerContext) =
      logger.warn(s"[$title] $message")
    def warnWithTitle(title: => String, message: => String, error: => Throwable)(implicit mc: MarkerContext) =
      logger.warn(s"[$title] $message", error)

    def errorWithTitle(title: => String, message: => String)(implicit mc: MarkerContext) =
      logger.error(s"[$title] $message")
    def errorWithTitle(title: => String, message: => String, error: => Throwable)(implicit mc: MarkerContext) =
      logger.error(s"[$title] $message", error)

  }

}
