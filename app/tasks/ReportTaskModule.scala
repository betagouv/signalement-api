package tasks

import play.api.inject.{SimpleModule, _}

class ReportTaskModule extends SimpleModule(bind[ReportTask].toSelf.eagerly())
