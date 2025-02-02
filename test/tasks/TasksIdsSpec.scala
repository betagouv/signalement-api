package tasks

import org.specs2.matcher.FutureMatchers
import org.specs2.mutable
import utils.TestApp

class TasksIdsSpec extends mutable.Specification with FutureMatchers {

  val (app, components) = TestApp.buildApp()

  "All scheduled tasks" should {
    "should not have duplicated taskId" in {
      val tasksids = components.allTasks.map(_.taskId)
      val duplicates = tasksids
        .groupBy(identity)
        .collect {
          case (value, occurrences) if occurrences.size > 1 => value
        }
        .toList
      duplicates shouldEqual Nil
    }

    "should not have duplicated taskNames" in {
      val taskNames = components.allTasks.map(_.taskName)
      val duplicates = taskNames
        .groupBy(identity)
        .collect {
          case (value, occurrences) if occurrences.size > 1 => value
        }
        .toList
      duplicates shouldEqual Nil
    }
  }
}
