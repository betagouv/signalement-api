package models

import org.specs2.mutable.Specification
import models.DetailInputValue._

class DetailInputValueTest extends Specification {

  "DetailInputValueTest" should {

    "string2detailInputValue" in {
      toDetailInputValue("label : value") must equalTo(DetailInputValue("label :", "value"))
      toDetailInputValue("value") must equalTo(DetailInputValue("Précision :", "value"))
    }

  }
}
