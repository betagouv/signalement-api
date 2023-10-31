package repositories.gs1

import io.scalaland.chimney.dsl.TransformerOps
import models.gs1.GS1Product
import models.gs1.GS1Product.GS1NetContent

import java.util.UUID

case class GS1NetContentRow(
    unitCode: Option[String],
    quantity: Option[String],
    gs1ProductId: UUID
)

object GS1NetContentRow {

  def toDomain(row: GS1NetContentRow) =
    row.into[GS1NetContent].transform

  def fromDomain(gs1ProductId: UUID, gs1NetContent: GS1NetContent) =
    gs1NetContent
      .into[GS1NetContentRow]
      .withFieldConst(_.gs1ProductId, gs1ProductId)
      .transform

  def fromDomain(gs1Product: GS1Product): Option[List[GS1NetContentRow]] =
    gs1Product.netContent.map { netContents =>
      netContents.map(fromDomain(gs1Product.id, _))
    }
}
