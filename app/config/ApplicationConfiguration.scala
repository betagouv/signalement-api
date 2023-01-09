package config

case class ApplicationConfiguration(
    app: SignalConsoConfiguration,
    mail: EmailConfiguration,
    task: TaskConfiguration,
    flyway: FlywayConfiguration,
    amazonBucketName: String
)
