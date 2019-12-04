package tasks

import play.api.inject.{SimpleModule, _}

class ReportDataTaskModule extends SimpleModule(bind[ReportDataTask].toSelf.eagerly())
