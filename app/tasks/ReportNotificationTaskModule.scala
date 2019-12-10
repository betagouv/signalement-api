package tasks

import play.api.inject.{SimpleModule, _}

class ReportNotificationTaskModule extends SimpleModule(bind[ReportNotificationTask].toSelf.eagerly())
