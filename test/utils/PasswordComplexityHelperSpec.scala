package utils

import org.specs2.mutable.Specification

class PasswordComplexityHelperSpec extends Specification {

  "PasswordComplexityHelper" should {
    "check password complexity" in {

      def check(s: String) =
        PasswordComplexityHelper.isPasswordComplexEnough(s)

      "complex password should pass" >> {
        check("p@ssWord1234") must beTrue
      }

      "without enough chars should not pass" >> {
        check("p@W4") must beFalse
      }
      "without special char should not pass" >> {
        check("passWord1234") must beFalse
      }
      "without uppercase should not pass" >> {
        check("p@ssword1234") must beFalse
      }
      "without lowercase should not pass" >> {
        check("P@SSWORD1234") must beFalse
      }
      "without numbers should not pass" >> {
        check("p@ssWordnonumbers") must beFalse
      }
      "uppercase or lowercase with accents should be counted" >> {
        check("Éñ123456789-//-") must beTrue
      }

    }

  }

}
