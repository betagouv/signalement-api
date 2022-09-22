package config

import utils.EmailAddress

import scala.util.matching.Regex

case class EmailConfiguration(
    from: EmailAddress,
    contactAddress: EmailAddress,
    skipReportEmailValidation: Boolean,
    emailProvidersBlocklist: List[String],
    outboundEmailFilterRegex: Regex
)
