package models

import org.specs2.mutable.Specification
import models.DetailInputValue._

class DetailInputValueTest extends Specification {

  "DetailInputValueTest" should {

    "string2detailInputValue" in {
      string2detailInputValue("label : value") must equalTo(DetailInputValue("label :", "value"))
      string2detailInputValue("value") must equalTo(DetailInputValue("Précision :", "value"))
    }

  }
}
