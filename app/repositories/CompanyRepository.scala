package repositories

import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile
import utils.SIRET

import models._

@Singleton
class CompanyRepository @Inject()(dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext) {

  private val dbConfig = dbConfigProvider.get[JdbcProfile]
  import PostgresProfile.api._
  import dbConfig._

  class CompanyTable(tag: Tag) extends Table[Company](tag, "companies") {
    def id = column[UUID]("id", O.PrimaryKey)
    def siret = column[SIRET]("siret", O.Unique)
    def creationDate = column[OffsetDateTime]("creation_date")
    def name = column[String]("name")
    def address = column[String]("address")
    def postalCode = column[Option[String]]("postal_code")

    def * = (id, siret, creationDate, name, address, postalCode) <> (Company.tupled, Company.unapply)
  }

  val companyTableQuery = TableQuery[CompanyTable]

  def getOrCreate(siret: SIRET, data: Company): Future[Company] =
    db.run(companyTableQuery.filter(_.siret === siret).result.headOption).flatMap(
      _.map(Future(_)).getOrElse(db.run(companyTableQuery returning companyTableQuery += data))
    )

  def findBySiret(siret: SIRET): Future[Option[Company]] =
    db.run(companyTableQuery.filter(_.siret === siret).result.headOption)
}
