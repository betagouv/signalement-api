package repositories.company

import models.Address
import models.Company
import slick.lifted.TableQuery
import utils.Constants.Departments
import utils.SIRET
import repositories.PostgresProfile.api._

import java.util.UUID
import java.time.OffsetDateTime

class CompanyTable(tag: Tag) extends Table[Company](tag, "companies") {
  def id = column[UUID]("id", O.PrimaryKey)
  def siret = column[SIRET]("siret", O.Unique)
  def creationDate = column[OffsetDateTime]("creation_date")
  def name = column[String]("name")
  def streetNumber = column[Option[String]]("street_number")
  def street = column[Option[String]]("street")
  def addressSupplement = column[Option[String]]("address_supplement")
  def city = column[Option[String]]("city")
  def postalCode = column[Option[String]]("postal_code")
  def department = column[Option[String]]("department")
  def activityCode = column[Option[String]]("activity_code")

  type CompanyData = (
      UUID,
      SIRET,
      OffsetDateTime,
      String,
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String]
  )

  def constructCompany: CompanyData => Company = {
    case (
          id,
          siret,
          creationDate,
          name,
          streetNumber,
          street,
          addressSupplement,
          postalCode,
          city,
          _,
          activityCode
        ) =>
      Company(
        id = id,
        siret = siret,
        creationDate = creationDate,
        name = name,
        address = Address(
          number = streetNumber,
          street = street,
          addressSupplement = addressSupplement,
          postalCode = postalCode,
          city = city
        ),
        activityCode = activityCode
      )
  }

  def extractCompany: PartialFunction[Company, CompanyData] = {
    case Company(
          id,
          siret,
          creationDate,
          name,
          address,
          activityCode
        ) =>
      (
        id,
        siret,
        creationDate,
        name,
        address.number,
        address.street,
        address.addressSupplement,
        address.postalCode,
        address.city,
        address.postalCode.flatMap(Departments.fromPostalCode),
        activityCode
      )
  }

  def * = (
    id,
    siret,
    creationDate,
    name,
    streetNumber,
    street,
    addressSupplement,
    postalCode,
    city,
    department,
    activityCode
  ) <> (constructCompany, extractCompany.lift)
}

object CompanyTable {
  val table = TableQuery[CompanyTable]
}
