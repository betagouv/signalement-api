package models.gs1

import play.api.libs.json.JsPath
import play.api.libs.json.Json
import play.api.libs.json.Reads
import utils.SIREN
import io.scalaland.chimney.dsl.TransformerOps
import models.gs1.GS1APIProduct.ItemOffered

case class GS1APIProduct(
    itemOffered: ItemOffered
)

object GS1APIProduct {
  case class GS1ProductDescription(
      lang: String,
      value: String
  )

  implicit val readsDescriptionFromGS1API: Reads[GS1ProductDescription] = Json.reads[GS1ProductDescription]

  case class ItemOffered(
      gtin: String,
      productDescription: Option[List[GS1ProductDescription]],
      additionalPartyIdentificationValue: Option[SIREN],
      netContent: Option[List[GS1Product.GS1NetContent]]
  )

  implicit val readsNetContentFromGS1API: Reads[GS1Product.GS1NetContent] = Json.reads[GS1Product.GS1NetContent]

  implicit val readsItemOfferedFromGS1API: Reads[ItemOffered] = Json.reads[ItemOffered]

  implicit val readsFromGS1API: Reads[GS1APIProduct] =
    (JsPath \ "itemOffered").read[ItemOffered].map(GS1APIProduct.apply)

  def toDomain(product: GS1APIProduct): GS1Product =
    product
      .into[GS1Product]
      .withFieldComputed(_.gtin, _.itemOffered.gtin)
      .withFieldComputed(_.siren, _.itemOffered.additionalPartyIdentificationValue)
      .withFieldComputed(
        _.description,
        p =>
          p.itemOffered.productDescription.flatMap { descriptions =>
            descriptions
              .find(_.lang == "fr")
              .orElse(descriptions.find(_.lang == "en"))
              .orElse(descriptions.headOption)
              .map(_.value)
          }
      )
      .withFieldComputed(_.netContent, _.itemOffered.netContent)
      .enableDefaultValues
      .transform
}
