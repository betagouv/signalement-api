package repositories.socialnetwork
import models.company.Company
import models.report.SocialNetworkSlug
import models.socialnetwork.SocialNetwork
import repositories.company.CompanyTable
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery
import repositories.PostgresProfile.api._

import scala.concurrent.Future

class SocialNetworkRepository(dbConfig: DatabaseConfig[JdbcProfile]) extends SocialNetworkRepositoryInterface {

  val table: TableQuery[SocialNetworkTable] = SocialNetworkTable.table

  import dbConfig._

  override def findCompanyBySocialNetworkSlug(slug: SocialNetworkSlug): Future[Option[Company]] = db.run(
    table
      .filter(_.slug === slug)
      .join(CompanyTable.table)
      .on(_.siret === _.siret)
      .map(_._2)
      .result
      .headOption
  )

  override def get(slug: SocialNetworkSlug): Future[Option[SocialNetwork]] = db.run(
    table
      .filter(_.slug === slug)
      .result
      .headOption
  )
}
