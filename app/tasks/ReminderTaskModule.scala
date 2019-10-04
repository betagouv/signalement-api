package tasks

import play.api.inject.{SimpleModule, _}

class ReminderTaskModule extends SimpleModule(bind[ReminderTask].toSelf.eagerly())
