package config

case class FlywayConfiguration(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    baselineOnMigrate: Boolean
) {
  def jdbcUrl = s"jdbc:postgresql://$host:$port/$database"
}
