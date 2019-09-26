package tasks

import play.api.inject.{SimpleModule, _}

class TasksModule extends SimpleModule(bind[ReportTask].toSelf.eagerly(), bind[ReminderTask].toSelf.eagerly())
