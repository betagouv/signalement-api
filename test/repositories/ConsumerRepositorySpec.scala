package repositories

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.matcher.TraversableMatchers
import org.specs2.mutable
import org.specs2.specification.BeforeAfterAll
import utils.Fixtures
import utils.TestApp

import java.time.OffsetDateTime
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class ConsumerRepositorySpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with TraversableMatchers
    with FutureMatchers
    with BeforeAfterAll {

  val (app, components) = TestApp.buildApp()

  val consumer = Fixtures.genConsumer.sample.get
  val deletedConsumer = Fixtures.genConsumer.sample.get.copy(deleteDate = Some(OffsetDateTime.now()))

  "ConsumerRepository" should {
    "getAll" should {
      "exclude soft deleted users" in {
        for {
          res <- components.consumerRepository.getAll()
        } yield res.map(_.id) shouldEqual Seq(consumer.id)
      }
    }
  }

  override def beforeAll(): Unit = {
    Await.result(components.consumerRepository.create(consumer), Duration.Inf)
    Await.result(components.consumerRepository.create(deletedConsumer), Duration.Inf)
    ()
  }

  override def afterAll(): Unit = {
    Await.result(components.consumerRepository.delete(consumer.id), Duration.Inf)
    Await.result(components.consumerRepository.delete(deletedConsumer.id), Duration.Inf)
    ()
  }
}
