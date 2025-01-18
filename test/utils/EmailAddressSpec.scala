package utils

import cats.implicits.catsSyntaxEitherId
import controllers.error.AppError.InvalidEmail
import org.specs2.mutable.Specification

class EmailAddressSpec extends Specification {
  "EmailAddress" should {
    "should compare gmail with classic email" in {
      EmailAddress("te.st@test.com").split.map(_.isEquivalentTo("te.st@gmail.com")) shouldEqual false.asRight
    }

    "should fail on invalid email" in {
      EmailAddress("gandtom+comm@commercial@gmail.com").split.map(_.isEquivalentTo("te.st@gmail.com")) shouldEqual Left(
        InvalidEmail("gandtom+comm@commercial@gmail.com")
      )
    }

    "should compare classic email" in {
      EmailAddress("te.st@test.com").split.map(_.isEquivalentTo("te.st@test.com")) shouldEqual true.asRight
    }

    "should compare classic email 2" in {
      EmailAddress("test@test.com").split.map(_.isEquivalentTo("te.st@test.com")) shouldEqual false.asRight
    }

    "should compare classic email 3" in {
      EmailAddress("te.st@test.com").split.map(_.isEquivalentTo("test@test.com")) shouldEqual false.asRight
    }

    "should compare gmail email 1" in {
      EmailAddress("te.st@gmail.com").split.map(_.isEquivalentTo("test@gmail.com")) shouldEqual true.asRight
    }

    "should compare gmail email 2" in {
      EmailAddress("test@gmail.com").split.map(_.isEquivalentTo("te.st@gmail.com")) shouldEqual true.asRight
    }

    "should compare gmail email 3" in {
      EmailAddress("t.e.st@gmail.com").split.map(_.isEquivalentTo("te.st+ahahah@gmail.com")) shouldEqual true.asRight
    }

    "should compare gmail email 4" in {
      EmailAddress("t.e.st2@gmail.com").split.map(_.isEquivalentTo("te.st+ahahah@gmail.com")) shouldEqual false.asRight
    }
  }
}
