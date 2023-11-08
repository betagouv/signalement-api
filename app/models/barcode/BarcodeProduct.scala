package models.barcode

import models.company.Address
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

import java.time.OffsetDateTime
import java.util.UUID

case class BarcodeProduct(
    id: UUID = UUID.randomUUID(),
    gtin: String,
    gs1Product: JsValue,
    openFoodFactsProduct: Option[JsValue],
    creationDate: OffsetDateTime = OffsetDateTime.now()
)

object BarcodeProduct {

  def fromAPI(gtin: String, gs1Product: JsValue, openFoodFactsProduct: Option[JsValue]): BarcodeProduct =
    BarcodeProduct(
      gtin = gtin,
      gs1Product = gs1Product,
      openFoodFactsProduct = openFoodFactsProduct
    )

  case class LangAndValue(lang: String, value: String)
  implicit val readsLangAndValue: Reads[LangAndValue] = Json.reads[LangAndValue]

  private def extractFrOrEnOrFirstLang(langAndValues: List[LangAndValue]): Option[String] =
    langAndValues
      .find(_.lang == "fr")
      .orElse(langAndValues.find(_.lang == "en"))
      .orElse(langAndValues.headOption)
      .map(_.value)

  private def extractAddress(json: JsValue) = Address(
    postalCode = (json \ "postalCode").asOpt[String],
    city = (json \ "addressLocality").asOpt[LangAndValue].map(_.value)
    //      country = (json \ "addressCountry").asOpt[String],
  )

  val writesToWebsite: OWrites[BarcodeProduct] = (product: BarcodeProduct) =>
    Json.obj(
      "id"    -> product.id,
      "gtin"  -> product.gtin,
      "siren" -> (product.gs1Product \ "itemOffered" \ "additionalPartyIdentificationValue").asOpt[String],
      "description" -> (product.gs1Product \ "itemOffered" \ "productDescription")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang)
        .orElse(product.openFoodFactsProduct.flatMap(extractProductNameFromOpenFoodFacts))
    )

  private def extractProductNameFromOpenFoodFacts(product: JsValue) =
    (product \ "product" \ "product_name_fr")
      .asOpt[String]
      .orElse((product \ "product" \ "product_name").asOpt[String])

  val writesToDashboard: OWrites[BarcodeProduct] = (product: BarcodeProduct) =>
    Json.obj(
      "id"   -> product.id,
      "gtin" -> product.gtin,
      "productDescription" -> (product.gs1Product \ "itemOffered" \ "productDescription")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang)
        .orElse(product.openFoodFactsProduct.flatMap(extractProductNameFromOpenFoodFacts)),
      "brandName" -> (product.gs1Product \ "itemOffered" \ "brand" \ "brandName")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang),
      "subBrandName" -> (product.gs1Product \ "itemOffered" \ "brand" \ "subBrandName")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang),
      "netContent" -> (product.gs1Product \ "itemOffered" \ "netContent").asOpt[JsValue],
      "globalLocationNumber" -> (product.gs1Product \ "itemOffered" \ "brandOwner" \ "globalLocationNumber")
        .asOpt[String],
      "companyName"   -> (product.gs1Product \ "itemOffered" \ "brandOwner" \ "companyName").asOpt[String],
      "postalAddress" -> (product.gs1Product \ "itemOffered" \ "postalAddress").asOpt[JsValue].map(extractAddress),
      "siren"         -> (product.gs1Product \ "itemOffered" \ "additionalPartyIdentificationValue").asOpt[String],
      "existOnOpenFoodFacts" -> product.openFoodFactsProduct.isDefined
    )
}
