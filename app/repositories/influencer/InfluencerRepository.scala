package repositories.influencer

import models.report.SocialNetworkSlug
import models.report.socialnetwork.CertifiedInfluencer
import play.api.Logger
import repositories.CRUDRepository
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext
class InfluencerRepository(override val dbConfig: DatabaseConfig[JdbcProfile])(implicit
    override val ec: ExecutionContext
) extends CRUDRepository[InfluencerTable, CertifiedInfluencer]
    with InfluencerRepositoryInterface {

  val logger: Logger                     = Logger(this.getClass)
  val table: TableQuery[InfluencerTable] = InfluencerTable.table
  import dbConfig._

  override def get(name: String, socialNetwork: SocialNetworkSlug) = db.run(
    table
      .filter { result =>
        (result.name <-> (name: String)).<(0.55)
      }
      .filter(_.socialNetwork === socialNetwork)
      .result
  )

}
