package models.gs1

import play.api.libs.functional.syntax._
import play.api.libs.json.JsPath
import play.api.libs.json.Reads
import utils.SIREN
import io.scalaland.chimney.dsl.TransformerOps

case class GS1APIProduct(
    gtin: String,
    siren: SIREN
)

object GS1APIProduct {
  implicit val readsFromGS1API: Reads[GS1APIProduct] = (
    (JsPath \ "itemOffered" \ "gtin").read[String] and
      (JsPath \ "itemOffered" \ "additionalPartyIdentificationValue").read[SIREN]
  )(GS1APIProduct.apply _)

  def toDomain(product: GS1APIProduct): GS1Product =
    product
      .into[GS1Product]
      .enableDefaultValues
      .transform
}
