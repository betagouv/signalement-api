package repositories

import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAfterAll
import repositories.PostgresProfile.api._
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import utils.TestApp

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class TestModel(id: UUID, s: String)

class TestModelTable(tag: Tag) extends DatabaseTable[TestModel](tag, "test_model") {
  def s = column[String]("s")

  override def * = (id, s) <> ((TestModel.apply _).tupled, TestModel.unapply)
}

object TestModelTable {
  val table = TableQuery[TestModelTable]

}

class CRUDRepositorySpec extends Specification with FutureMatchers with BeforeAfterAll {

  val (app, components) = TestApp.buildApp()
  implicit val ec = components.executionContext
  val db = components.dbConfig.db

  val repository = new CRUDRepository[TestModelTable, TestModel]() {
    override val table = TestModelTable.table
    implicit override val ec: ExecutionContext = components.executionContext
    override val dbConfig: DatabaseConfig[JdbcProfile] = components.dbConfig
  }

  override def beforeAll(): Unit =
    Await.result(db.run(TestModelTable.table.schema.create), Duration.Inf)

  override def afterAll(): Unit =
    Await.result(db.run(TestModelTable.table.schema.drop), Duration.Inf)

  "CRUDRepository" should {
    "create and delete a model" in {
      val id = UUID.randomUUID()
      val s = UUID.randomUUID().toString
      for {
        afterCreate <- repository.create(TestModel(id, s))
        get1 <- repository.get(id)
        _ <- repository.delete(id)
        get2 <- repository.get(id)
      } yield (afterCreate shouldEqual TestModel(id, s)) &&
        (get1 shouldEqual Some(TestModel(id, s))) &&
        (get2 shouldEqual None)
    }

    "update a model" in {
      val id = UUID.randomUUID()
      UUID.randomUUID().toString
      val s2 = UUID.randomUUID().toString
      for {
        _ <- repository.create(TestModel(id, s2))
        _ <- repository.update(id, TestModel(id, s2))
        get <- repository.get(id)
        _ <- repository.delete(id)
      } yield get shouldEqual Some(TestModel(id, s2))
    }

    "createOrUpdate a model and list models" in {
      val id1 = UUID.randomUUID()
      val id2 = UUID.randomUUID()
      val s0 = UUID.randomUUID().toString
      val s1 = UUID.randomUUID().toString
      val s2 = UUID.randomUUID().toString
      for {
        _ <- repository.createOrUpdate(TestModel(id1, s0))
        _ <- repository.createOrUpdate(TestModel(id2, s1))
        _ <- repository.createOrUpdate(TestModel(id2, s2))
        list <- repository.list()
      } yield list shouldEqual List(TestModel(id1, s0), TestModel(id2, s2))
    }
  }
}
