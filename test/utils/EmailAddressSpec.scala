package utils

import org.specs2.mutable.Specification

class EmailAddressSpec extends Specification {
  "EmailAddress" should {
    "should compare gmail with classic email" in {
      EmailAddress("te.st@test.com").isEquivalentTo("te.st@gmail.com") should beFalse
    }

    "should compare classic email" in {
      EmailAddress("te.st@test.com").isEquivalentTo("te.st@test.com") should beTrue
    }

    "should compare classic email 2" in {
      EmailAddress("test@test.com").isEquivalentTo("te.st@test.com") should beFalse
    }

    "should compare classic email 3" in {
      EmailAddress("te.st@test.com").isEquivalentTo("test@test.com") should beFalse
    }

    "should compare gmail email 1" in {
      EmailAddress("te.st@gmail.com").isEquivalentTo("test@gmail.com") should beTrue
    }

    "should compare gmail email 2" in {
      EmailAddress("test@gmail.com").isEquivalentTo("te.st@gmail.com") should beTrue
    }

    "should compare gmail email 3" in {
      EmailAddress("t.e.st@gmail.com").isEquivalentTo("te.st+ahahah@gmail.com") should beTrue
    }

    "should compare gmail email 4" in {
      EmailAddress("t.e.st2@gmail.com").isEquivalentTo("te.st+ahahah@gmail.com") should beFalse
    }
  }
}
