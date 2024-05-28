import org.specs2.mutable.Specification
import utils.AppSpec

class ConfigurationSpec extends Specification with AppSpec {

  "Configuration should be parsed" in {
    emailConfiguration.emailProvidersBlocklist mustEqual List("yopmail.com", "another.com")
  }
}
