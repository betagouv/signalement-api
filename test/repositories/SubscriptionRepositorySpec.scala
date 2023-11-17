package repositories

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable
import utils.AppSpec
import utils.Fixtures
import utils.TestApp

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SubscriptionRepositorySpec(implicit ee: ExecutionEnv)
    extends mutable.Specification
    with AppSpec
    with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  val user1         = Fixtures.genUser.sample.get
  val user2         = Fixtures.genUser.sample.get
  val subscritpion1 = Fixtures.genSubscriptionFor(Some(user1.id)).sample.get
  val subscritpion2 = Fixtures.genSubscriptionFor(Some(user1.id)).sample.get
  val subscritpion3 = Fixtures.genSubscriptionFor(None).sample.get

  override def setupData() = {
    Await.result(components.userRepository.create(user1), Duration.Inf)
    Await.result(components.userRepository.create(user2), Duration.Inf)
    Await.result(components.subscriptionRepository.create(subscritpion1), Duration.Inf)
    Await.result(components.subscriptionRepository.create(subscritpion2), Duration.Inf)
    Await.result(components.subscriptionRepository.create(subscritpion3), Duration.Inf)
    ()
  }

  "SubscriptionRepository" should {
    "findFor" in {
      for {
        res1 <- components.subscriptionRepository.findFor(user1, subscritpion1.id)
        res2 <- components.subscriptionRepository.findFor(user1, subscritpion3.id)
        res3 <- components.subscriptionRepository.findFor(user2, subscritpion1.id)
      } yield (res1.isDefined shouldEqual true) && (res2.isEmpty shouldEqual true) && (res3.isEmpty shouldEqual true)
    }

    "deleteFor" in {
      for {
        before <- components.subscriptionRepository.findFor(user1, subscritpion2.id)
        res    <- components.subscriptionRepository.deleteFor(user1, subscritpion2.id)
        after  <- components.subscriptionRepository.findFor(user1, subscritpion2.id)
      } yield (before.isDefined shouldEqual true) && (res shouldEqual 1) && (after.isEmpty shouldEqual true)
    }
  }

}
