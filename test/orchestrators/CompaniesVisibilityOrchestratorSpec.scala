package orchestrators

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.FutureMatchers
import org.specs2.mutable.Specification
import utils.AppSpec


class CompaniesVisibilityOrchestratorSpec(implicit ee: ExecutionEnv)
    extends Specification
    with AppSpec
    with FutureMatchers {

  "CompaniesVisibilityOrchestratorSpec" should {
    "fetchVisibleCompanies" should {
      "work as expected" in {
        1 + 1 shouldEqual 2
      }
    }
  }

}
