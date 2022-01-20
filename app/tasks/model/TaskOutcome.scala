package tasks.model

import java.util.UUID

sealed trait TaskOutcome {
  val reportId: UUID
  val value: TaskType
}

object TaskOutcome {
  case class SuccessfulTask(reportId: UUID, value: TaskType) extends TaskOutcome
  case class FailedTask(reportId: UUID, value: TaskType, err: Throwable) extends TaskOutcome
}
