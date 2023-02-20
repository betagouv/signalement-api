package models

import play.api.libs.json.JsValue

import java.util.UUID

case class UserSettings(userId: UUID, reportsFilters: JsValue)
