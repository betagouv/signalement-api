package tasks

import play.api.inject._

class ReportNotificationTaskModule extends SimpleModule(bind[ReportNotificationTask].toSelf.eagerly())
