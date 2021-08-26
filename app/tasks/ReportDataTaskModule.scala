package tasks

import play.api.inject._

class ReportDataTaskModule extends SimpleModule(bind[ReportDataTask].toSelf.eagerly())
