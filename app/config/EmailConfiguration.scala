package config

import utils.EmailAddress

case class EmailConfiguration(
    from: EmailAddress,
    contactAddress: EmailAddress,
    skipReportEmailValidation: Boolean,
    ccrfEmailSuffix: String,
    emailProviderBlocklists: List[String]
)
