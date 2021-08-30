package tasks

import play.api.inject._

class ReminderTaskModule extends SimpleModule(bind[ReminderTask].toSelf.eagerly())
