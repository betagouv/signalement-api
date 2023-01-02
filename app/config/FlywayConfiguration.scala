package config

case class FlywayConfiguration(host: String, port: Int, database: String, user: String, password: String) {
  def jdbcUrl = s"jdbc:postgresql://$host:$port/$database"
}
