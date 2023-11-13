package config

case class ApplicationConfiguration(
    app: SignalConsoConfiguration,
    mail: EmailConfiguration,
    task: TaskConfiguration,
    flyway: FlywayConfiguration,
    siretExtractor: SiretExtractorConfiguration,
    gs1: GS1Configuration,
    amazonBucketName: String
)
