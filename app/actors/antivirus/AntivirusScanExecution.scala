package actors.antivirus

case class AntivirusScanExecution private (exitCode: Option[AntivirusScanExitCode], output: String)

object AntivirusScanExecution {
  val Ignore = AntivirusScanExecution(exitCode = None, output = "Scan is disabled")
  def apply(exitCode: AntivirusScanExitCode, output: String): AntivirusScanExecution =
    AntivirusScanExecution(Some(exitCode), output)
}
