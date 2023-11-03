package models.gs1

import models.company.Address
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.libs.json.OWrites
import play.api.libs.json.Reads

import java.time.OffsetDateTime
import java.util.UUID

case class GS1Product(
    id: UUID = UUID.randomUUID(),
    gtin: String,
    product: JsValue,
    creationDate: OffsetDateTime = OffsetDateTime.now()
)

object GS1Product {

  def fromAPI(gtin: String, json: JsValue): GS1Product = GS1Product(
    gtin = gtin,
    product = json
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

  val writesToWebsite: OWrites[GS1Product] = (gS1Product: GS1Product) =>
    Json.obj(
      "id"    -> gS1Product.id,
      "gtin"  -> gS1Product.gtin,
      "siren" -> (gS1Product.product \ "itemOffered" \ "additionalPartyIdentificationValue").asOpt[String],
      "description" -> (gS1Product.product \ "itemOffered" \ "productDescription")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang)
    )
  val writesToDashboard: OWrites[GS1Product] = (gS1Product: GS1Product) =>
    Json.obj(
      "id"   -> gS1Product.id,
      "gtin" -> gS1Product.gtin,
      "productDescription" -> (gS1Product.product \ "itemOffered" \ "productDescription")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang),
      "brandName" -> (gS1Product.product \ "itemOffered" \ "brand" \ "brandName")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang),
      "subBrandName" -> (gS1Product.product \ "itemOffered" \ "brand" \ "subBrandName")
        .asOpt[List[LangAndValue]]
        .flatMap(extractFrOrEnOrFirstLang),
      "netContent" -> (gS1Product.product \ "itemOffered" \ "netContent").asOpt[JsValue],
      "globalLocationNumber" -> (gS1Product.product \ "itemOffered" \ "brandOwner" \ "globalLocationNumber")
        .asOpt[String],
      "companyName"   -> (gS1Product.product \ "itemOffered" \ "brandOwner" \ "companyName").asOpt[String],
      "postalAddress" -> (gS1Product.product \ "itemOffered" \ "postalAddress").asOpt[JsValue].map(extractAddress),
      "siren"         -> (gS1Product.product \ "itemOffered" \ "additionalPartyIdentificationValue").asOpt[String]
    )
}
