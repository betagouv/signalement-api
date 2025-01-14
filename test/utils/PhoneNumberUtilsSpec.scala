package utils

import org.specs2.mutable.Specification
import utils.PhoneNumberUtils.sanitizeIncomingPhoneNumber

class PhoneNumberUtilsSpec extends Specification {
  "PhoneNumberUtils" should {

    "leave typical phone number as is" in {
      sanitizeIncomingPhoneNumber("0545653312") shouldEqual "0545653312"
    }

    "leave typical small phone number as is" in {
      sanitizeIncomingPhoneNumber("3605") shouldEqual "3605"
    }

    "remove spaces" in {
      sanitizeIncomingPhoneNumber("05 45 65 33 12") shouldEqual "0545653312"
    }

    "remove dots" in {
      sanitizeIncomingPhoneNumber("05.45.65.33.12") shouldEqual "0545653312"
    }

    "remove +33" in {
      sanitizeIncomingPhoneNumber("+33545653312") shouldEqual "0545653312"
    }

    "remove 0033" in {
      sanitizeIncomingPhoneNumber("0033545653312") shouldEqual "0545653312"
    }

    "remove a combination of all of that" in {
      sanitizeIncomingPhoneNumber("  +33 5 45.65.33. 12  ") shouldEqual "0545653312"
    }

  }
}
