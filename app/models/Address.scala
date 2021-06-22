package models

import play.api.libs.json.{Json, OFormat}
import utils.Country

case class Address(
  number: Option[String] = None,
  street: Option[String] = None,
  addressSupplement: Option[String] = None,
  postalCode: Option[String] = None,
  city: Option[String] = None,
  country: Option[Country] = None,
) {

  def isDefined: Boolean = (for {
    _ <- number
    _ <- street
    _ <- addressSupplement
    _ <- postalCode
    _ <- city
    _ <- country
  } yield true).getOrElse(false)

  def nonEmpty: Boolean = !isDefined

  private[this] def fullStreet: String = (number.getOrElse(" ") + street.getOrElse("")).trim()

  private[this] def fullCity: String = (postalCode.getOrElse(" ") + city.getOrElse("")).trim()

  def toArray: Seq[String] = Seq(
    fullStreet,
    addressSupplement.getOrElse(""),
    fullCity,
    country.map(_.name).getOrElse("")
  ).filter(_ != "")

  override def toString: String = toArray.mkString(" - ")
}

object Address {
  implicit val addressFormat: OFormat[Address] = Json.format[Address]

  //  def writes(report: Report) =
  //    Json.obj(
  //      "id" -> report.id,
  //      "category" -> report.category,
  //      "subcategories" -> report.subcategories,
  //      "details" -> report.details,
  //      "companyName" -> report.companyName,
  //      "companyAddress" -> Json.toJson(report.companyAddress),
  //      "companySiret" -> report.companySiret,
  //      "creationDate" -> report.creationDate,
  //      "contactAgreement" -> report.contactAgreement,
  //      "employeeConsumer" -> report.employeeConsumer,
  //      "status" -> report.status,
  //      "websiteURL" -> report.websiteURL,
  //      "phone" -> report.phone,
  //      "vendor" -> report.vendor,
  //      "tags" -> report.tags
  //    ) ++ ((userRole, report.contactAgreement) match {
  //      case (Some(UserRoles.Pro), false) => Json.obj()
  //      case (_, _) => Json.obj(
  //        "firstName" -> report.firstName,
  //        "lastName" -> report.lastName,
  //        "email" -> report.email
  //      )
  //    })
}