package models.report

import enumeratum.EnumEntry
import enumeratum.PlayEnum
import repositories.PostgresProfile.api._
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType

sealed trait SocialNetworkSlug extends EnumEntry

object SocialNetworkSlug extends PlayEnum[SocialNetworkSlug] {
  override def values: IndexedSeq[SocialNetworkSlug] = findValues

  case object YouTube   extends SocialNetworkSlug
  case object Facebook  extends SocialNetworkSlug
  case object Instagram extends SocialNetworkSlug
  case object TikTok    extends SocialNetworkSlug
  case object Twitter   extends SocialNetworkSlug
  case object LinkedIn  extends SocialNetworkSlug
  case object Snapchat  extends SocialNetworkSlug
  case object Twitch    extends SocialNetworkSlug

  implicit val socialNetworkSlugColumnType: JdbcType[SocialNetworkSlug] with BaseTypedType[SocialNetworkSlug] =
    MappedColumnType.base[SocialNetworkSlug, String](
      _.entryName,
      SocialNetworkSlug.withName
    )

}
