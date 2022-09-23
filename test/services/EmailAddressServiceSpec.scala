package services

import org.specs2.mutable.Specification

class EmailAddressServiceSpec extends Specification {

  "EmailAddressService" should {
    "check admin email" in {

      def check(email: String) =
        EmailAddressService.isEmailAcceptableForAdminAccount(email)

      "deny random email" >> {
        check("foo@gmail.com") must beFalse
      }
      "accept @dgccrf.finances.gouv.fr" >> {
        check("foo@dgccrf.finances.gouv.fr") must beTrue
      }
      "accept @beta.gouv.fr" >> {
        check("foo@beta.gouv.fr") must beTrue
      }
      "accept *.betagouv@gmail.com" >> {
        check("foo.betagouv@gmail.com") must beTrue
      }
      "accept *.betagouv+suffix@gmail.com" >> {
        check("foo.betagouv+whatever@gmail.com") must beTrue
      }
      "deny some tricky cases" >> {
        check("foo@other.gouv.fr") must beFalse
        check("foo@dgccrfxfinancesxgouv.fr") must beFalse
        check("foo@betaxgouv.fr") must beFalse
        check("foo@beta.gouv.froops") must beFalse
        check("foo.beta.gouv.fr@gmail.com") must beFalse
        check("foo.betagouv.other@gmail.com") must beFalse
      }
    }

    "check dgccrf email" in {

      def check(email: String) =
        EmailAddressService.isEmailAcceptableForDgccrfAccount(email)

      "deny random email" >> {
        check("foo@gmail.com") must beFalse
      }
      "accept *.gouv.fr" >> {
        check("foo@bar.gouv.fr") must beTrue
      }
      "deny some tricky cases" >> {
        check("foo@bar.gouv.froops") must beFalse
        check("foo@gouv.fr") must beFalse
        check("foo.bar.gouv.fr@gmail.com") must beFalse
      }
    }

  }

}
