package repositories.gs1

import io.scalaland.chimney.dsl._
import models.gs1.GS1Product
import utils.SIREN

import java.time.OffsetDateTime
import java.util.UUID

case class GS1ProductRow(
    id: UUID,
    gtin: String,
    siren: Option[SIREN],
    description: Option[String],
    creationDate: OffsetDateTime
)

object GS1ProductRow {
  def fromDomain(gs1Product: GS1Product): GS1ProductRow =
    gs1Product.into[GS1ProductRow].transform

  def toDomain(row: GS1ProductRow, netContents: Option[List[GS1NetContentRow]]): GS1Product =
    row
      .into[GS1Product]
      .withFieldConst(_.netContent, netContents.map(_.map(GS1NetContentRow.toDomain)))
      .transform
}
