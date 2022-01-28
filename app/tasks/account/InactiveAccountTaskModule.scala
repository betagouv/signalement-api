package tasks.account

import play.api.inject._

class InactiveAccountTaskModule extends SimpleModule(bind[InactiveAccountTask].toSelf.eagerly())
