package models.website

import controllers.error.AppError.MalformedQueryParams
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.EnumEntry
import enumeratum.PlayEnum
import play.api.Logger

sealed trait WebsiteKind extends EnumEntry with UpperSnakecase

sealed trait DirectSellerIdentificationStatus extends WebsiteKind

object DirectSellerIdentificationStatus {
  val logger: Logger = Logger(this.getClass())

  def withName(kind: String): DirectSellerIdentificationStatus =
    WebsiteKind
      .withNameOption(kind)
      .filter(_.isInstanceOf[DirectSellerIdentificationStatus]) match {
      case Some(value: DirectSellerIdentificationStatus) => value
      case _ =>
        logger.error(s"Cannot parse $kind into a valid DirectSellerIdentificationStatus")
        throw MalformedQueryParams
    }

}

object WebsiteKind extends PlayEnum[WebsiteKind] {

  val values: IndexedSeq[WebsiteKind] = findValues

  final case object Default extends DirectSellerIdentificationStatus

  final case object Pending extends DirectSellerIdentificationStatus

  final case object Marketplace extends WebsiteKind

}
