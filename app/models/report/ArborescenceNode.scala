package models.report

import play.api.libs.json.JsArray
import play.api.libs.json.JsValue

case class ArborescenceNode(categoryName: Option[String], path: Vector[(String, NodeInfo)])

case class NodeInfo(id: String, tags: List[String])

object ArborescenceNode {
  def fromJson(json: JsArray): List[ArborescenceNode] =
    json.value.flatMap(from(_)).toList

  def from(
      json: JsValue,
      currentCategoryName: Option[String] = None,
      currentPath: Vector[(String, NodeInfo)] = Vector.empty
  ): List[ArborescenceNode] = {
    val id = (json \ "id").as[String]
    val title = (json \ "title").as[String]
    val category = (json \ "category").asOpt[String]
    val subcategories = (json \ "subcategories").asOpt[List[JsValue]].getOrElse(List.empty)
    val tags = (json \ "tags").asOpt[List[String]].getOrElse(List.empty)

    subcategories match {
      case Nil =>
        List(ArborescenceNode(currentCategoryName, currentPath :+ (title, NodeInfo(id, tags))))
      case _ =>
        category match {
          case Some(cat) =>
            subcategories.flatMap(jsValue => from(jsValue, Some(title), currentPath :+ (cat, NodeInfo(id, tags))))
          case None =>
            subcategories.flatMap(jsValue =>
              from(jsValue, currentCategoryName, currentPath :+ (title, NodeInfo(id, tags)))
            )
        }
    }
  }
}
