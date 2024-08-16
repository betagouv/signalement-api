package models.barcode

import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

import java.time.OffsetDateTime
import java.util.UUID

case class BarcodeProduct(
    id: UUID = UUID.randomUUID(),
    gtin: String,
    gs1Product: Option[JsValue],
    openFoodFactsProduct: Option[JsValue],
    openBeautyFactsProduct: Option[JsValue],
    creationDate: OffsetDateTime = OffsetDateTime.now(),
    updateDate: OffsetDateTime = OffsetDateTime.now()
)

object BarcodeProduct {

  case class LangAndValue(lang: String, value: String)
  implicit val readsLangAndValue: Reads[LangAndValue] = Json.reads[LangAndValue]

  private def extractFrOrEnOrFirstLangFromGS1(langAndValues: List[LangAndValue]): Option[String] =
    langAndValues
      .find(_.lang == "fr")
      .orElse(langAndValues.find(_.lang == "en"))
      .orElse(langAndValues.headOption)
      .map(_.value)

  private def extractProductNameFromOpenXFacts(product: JsValue): Option[String] =
    (product \ "product" \ "product_name_fr")
      .asOpt[String]
      .filter(_.nonEmpty)
      .orElse((product \ "product" \ "product_name").asOpt[String].filter(_.nonEmpty))

  private def extractBrandNameFromOpenXFacts(product: JsValue): Option[String] =
    (product \ "product" \ "brands").asOpt[String]

  private def extractPackagingFromOpenXFacts(product: JsValue): Option[String] =
    (product \ "product" \ "packaging_text_fr")
      .asOpt[String]
      .filter(_.nonEmpty)
      .orElse((product \ "product" \ "packaging_text").asOpt[String].filter(_.nonEmpty))
      .orElse((product \ "product" \ "packaging").asOpt[String].filter(_.nonEmpty))

  private def extractEMBFromOpenXFacts(product: JsValue): Option[String] =
    (product \ "product" \ "emb_codes").asOpt[String].filter(_.nonEmpty)

  private def extractProductName(product: BarcodeProduct): Option[String] =
    product.openFoodFactsProduct
      .flatMap(extractProductNameFromOpenXFacts)
      .orElse(product.openBeautyFactsProduct.flatMap(extractProductNameFromOpenXFacts))
      .orElse(
        product.gs1Product
          .flatMap(json => (json \ "itemOffered" \ "productDescription").asOpt[List[LangAndValue]])
          .flatMap(extractFrOrEnOrFirstLangFromGS1)
      )

  private def extractBrandName(product: BarcodeProduct): Option[String] =
    product.openFoodFactsProduct
      .flatMap(extractBrandNameFromOpenXFacts)
      .orElse(product.openBeautyFactsProduct.flatMap(extractBrandNameFromOpenXFacts))
      .orElse(
        product.gs1Product
          .flatMap(json => (json \ "itemOffered" \ "brand" \ "brandName").asOpt[List[LangAndValue]])
          .flatMap(extractFrOrEnOrFirstLangFromGS1)
      )

  private def extractPackaging(product: BarcodeProduct): Option[String] =
    product.openFoodFactsProduct
      .flatMap(extractPackagingFromOpenXFacts)
      .orElse(product.openBeautyFactsProduct.flatMap(extractPackagingFromOpenXFacts))

  private def extractEMB(product: BarcodeProduct): Option[String] =
    product.openFoodFactsProduct
      .flatMap(extractEMBFromOpenXFacts)
      .orElse(product.openBeautyFactsProduct.flatMap(extractEMBFromOpenXFacts))

  val writesToWebsite: OWrites[BarcodeProduct] = (product: BarcodeProduct) =>
    Json.obj(
      "id"   -> product.id,
      "gtin" -> product.gtin,
      "siren" -> product.gs1Product.flatMap(json =>
        (json \ "itemOffered" \ "additionalPartyIdentificationValue").asOpt[String]
      ),
      "productName" -> extractProductName(product)
    )

  val writesToDashboard: OWrites[BarcodeProduct] = (product: BarcodeProduct) =>
    Json.obj(
      "id"                     -> product.id,
      "gtin"                   -> product.gtin,
      "productName"            -> extractProductName(product),
      "brandName"              -> extractBrandName(product),
      "emb_codes"              -> extractEMB(product),
      "packaging"              -> extractPackaging(product),
      "existOnOpenFoodFacts"   -> product.openFoodFactsProduct.isDefined,
      "existOnOpenBeautyFacts" -> product.openBeautyFactsProduct.isDefined
    )
}
