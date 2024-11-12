package models.report

import play.api.libs.json.JsArray
import play.api.libs.json.JsValue

case class ArborescenceNode(categoryName: Option[CategoryInfo], path: Vector[(CategoryInfo, NodeInfo)])

case class NodeInfo(id: String, tags: List[String])

case class CategoryInfo(key: String, label: String)

object ArborescenceNode {
  def fromJson(json: JsArray): List[ArborescenceNode] =
    json.value.flatMap(from(_)).toList

  def from(
      json: JsValue,
      currentCategory: Option[CategoryInfo] = None,
      currentPath: Vector[(CategoryInfo, NodeInfo)] = Vector.empty
  ): List[ArborescenceNode] = {
    val id            = (json \ "id").as[String]
    val title         = (json \ "title").as[String]
    val category      = (json \ "category").asOpt[String]
    val subcategory   = (json \ "subcategory").asOpt[String]
    val subcategories = (json \ "subcategories").asOpt[List[JsValue]].getOrElse(List.empty)
    val tags          = (json \ "tags").asOpt[List[String]].getOrElse(List.empty)

    subcategories match {
      case Nil =>
        List(
          ArborescenceNode(
            currentCategory,
            currentPath :+ (CategoryInfo(subcategory.orElse(category).get, title), NodeInfo(id, tags))
          )
        )
      case _ =>
        category match {
          case Some(cat) =>
            subcategories.flatMap(jsValue =>
              from(
                jsValue,
                Some(CategoryInfo(cat, title)),
                currentPath :+ (CategoryInfo(cat, title), NodeInfo(id, tags))
              )
            )
          case None =>
            subcategories.flatMap(jsValue =>
              from(jsValue, currentCategory, currentPath :+ (CategoryInfo(subcategory.get, title), NodeInfo(id, tags)))
            )
        }
    }
  }
}
