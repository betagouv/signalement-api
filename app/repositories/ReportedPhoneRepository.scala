package repositories

import java.time.OffsetDateTime
import java.util.UUID

import javax.inject.{Inject, Singleton}
import models._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ReportedPhoneRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, val companyRepository: CompanyRepository)
  (implicit ec: ExecutionContext) {

  val logger: Logger = Logger(this.getClass)
  private val dbConfig = dbConfigProvider.get[JdbcProfile]

  import PostgresProfile.api._
  import dbConfig._

  implicit val ReportedPhoneStatusColumnType = MappedColumnType.base[ReportedPhoneStatus, String](_.value, ReportedPhoneStatus.fromValue)

  class ReportedPhoneTable(tag: Tag) extends Table[ReportedPhone](tag, "reported_phones") {
    def id = column[UUID]("id", O.PrimaryKey)
    def creationDate = column[OffsetDateTime]("creation_date")
    def phone = column[String]("phone")
    def companyId = column[UUID]("company_id")
    def status = column[ReportedPhoneStatus]("status")
    def * = (id, creationDate, phone, companyId, status) <> ((ReportedPhone.apply _).tupled, ReportedPhone.unapply)
  }

  val websiteTableQuery = TableQuery[ReportedPhoneTable]

  def find(id: UUID): Future[Option[ReportedPhone]] = db
    .run(websiteTableQuery.filter(_.id === id).to[List].result.headOption)

  def update(website: ReportedPhone): Future[ReportedPhone] = {
    val query = for (refReportedPhone <- websiteTableQuery if refReportedPhone.id === website.id)
      yield refReportedPhone
    db.run(query.update(website))
      .map(_ => website)
  }

  def create(newReportedPhone: ReportedPhone): Future[ReportedPhone] =
    db.run(websiteTableQuery
      .filter(_.phone === newReportedPhone.phone)
      .filter(website => (website.status === ReportedPhoneStatus.VALIDATED) || (website.companyId === newReportedPhone.companyId))
      .result.headOption
    )
      .flatMap(_.map(Future(_))
      .getOrElse(db.run(websiteTableQuery returning websiteTableQuery += newReportedPhone)))

  def searchCompaniesByPhone(phone: String, statusList: Option[Seq[ReportedPhoneStatus]] = None): Future[Seq[(ReportedPhone, Company)]] = {
    db.run(websiteTableQuery
      .filter(_.phone === phone)
      .filter(w => statusList.fold(true.bind)(w.status.inSet(_)))
      .join(companyRepository.companyTableQuery).on(_.companyId === _.id)
      .result
    )
  }

  def list(): Future[List[(ReportedPhone, Company)]] = db.run(websiteTableQuery
    .join(companyRepository.companyTableQuery).on(_.companyId === _.id)
    .to[List].result
  )

  def delete(id: UUID): Future[Int] = db.run(websiteTableQuery.filter(_.id === id).delete)
}
