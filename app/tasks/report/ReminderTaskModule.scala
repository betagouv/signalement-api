package tasks.report

import play.api.inject._

class ReminderTaskModule extends SimpleModule(bind[ReportTask].toSelf.eagerly())
