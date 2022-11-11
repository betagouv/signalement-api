package actors
import scala.concurrent.duration._

class EmailActorSpec extends org.specs2.mutable.Specification {

  "EmailActor" >> {
    "retry delay" >> {

      "must be very small after 1 attempt" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 1) must ===(Some(2.seconds))
      }
      "must be bigger after 2 attempts" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 2) must ===(Some(16.seconds))
      }
      "must be much bigger after 3 attempts" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 3) must ===(Some(54.seconds))
      }
      "must be much bigger after 4 attempts" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 4) must ===(Some(128.seconds))
      }
      "must be much bigger after 5 attempts" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 5) must ===(Some(250.seconds))
      }
      "must be much bigger after 6 attempts" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 6) must ===(Some(432.seconds))
      }
      "must stop after 7 attempts" >> {
        EmailActor.getDelayBeforeNextRetry(nbPastAttempts = 7) must ===(None)
      }
      "total retry time must be 882s" >> {

        def buildTotalDuration(attemptsSoFar: Int = 1, durationSoFar: FiniteDuration = Duration.Zero): FiniteDuration =
          EmailActor.getDelayBeforeNextRetry(attemptsSoFar) match {
            case Some(newDelay) =>
              buildTotalDuration(attemptsSoFar + 1, durationSoFar + newDelay)
            case None =>
              durationSoFar
          }
        buildTotalDuration() must ===(882.seconds)
      }
    }

  }
}
