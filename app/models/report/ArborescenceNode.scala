package models.report

import play.api.libs.json.JsArray
import play.api.libs.json.JsValue

case class ArborescenceNode(overriddenCategory: Option[CategoryInfo], path: Vector[(CategoryInfo, NodeInfo)])

case class NodeInfo(id: String, tags: List[String], isBlocking: Boolean)

case class CategoryInfo(key: String, label: String)

object ArborescenceNode {

  private def extractCategories(json: JsArray): Map[String, (CategoryInfo, NodeInfo)] =
    json.value.map { jsValue =>
      val id         = (jsValue \ "id").as[String]
      val title      = (jsValue \ "title").as[String]
      val category   = (jsValue \ "category").as[String]
      val tags       = (jsValue \ "tags").asOpt[List[String]].getOrElse(List.empty)
      val isBlocking = (jsValue \ "isBlocking").asOpt[Boolean].getOrElse(false)

      category -> (CategoryInfo(category, title) -> NodeInfo(id, tags, isBlocking))
    }.toMap

  def fromJson(json: JsArray): List[ArborescenceNode] = {
    val categories = extractCategories(json)

    json.value.flatMap(from(_, categories)).toList
  }

  private def replaceCategory(
      categoriesMap: Map[String, (CategoryInfo, NodeInfo)],
      categoryOverride: Option[String],
      path: Vector[(CategoryInfo, NodeInfo)]
  ): Vector[(CategoryInfo, NodeInfo)] = categoryOverride match {
    case Some(catOverride) => categoriesMap(catOverride) +: path.tail
    case None              => path
  }

  def from(
      json: JsValue,
      categoriesMap: Map[String, (CategoryInfo, NodeInfo)],
      currentCategory: Option[CategoryInfo] = None,
      currentCategoryOverride: Option[String] = None,
      currentPath: Vector[(CategoryInfo, NodeInfo)] = Vector.empty
  ): List[ArborescenceNode] = {
    val id               = (json \ "id").as[String]
    val title            = (json \ "title").as[String]
    val category         = (json \ "category").asOpt[String]
    val subcategory      = (json \ "subcategory").asOpt[String]
    val subcategories    = (json \ "subcategories").asOpt[List[JsValue]].getOrElse(List.empty)
    val tags             = (json \ "tags").asOpt[List[String]].getOrElse(List.empty)
    val isBlocking       = (json \ "isBlocking").asOpt[Boolean].getOrElse(false)
    val categoryOverride = (json \ "categoryOverride").asOpt[String]

    val combinedCategoryOverride = currentCategoryOverride.orElse(categoryOverride)

    val nodeInfo = NodeInfo(id, tags, isBlocking)
    subcategories match {
      case Nil =>
        List(
          ArborescenceNode(
            if (combinedCategoryOverride.isDefined) currentCategory else None,
            replaceCategory(
              categoriesMap,
              combinedCategoryOverride,
              currentPath :+ (CategoryInfo(subcategory.orElse(category).get, title), nodeInfo)
            )
          )
        )
      case _ =>
        category match {
          case Some(cat) =>
            subcategories.flatMap(jsValue =>
              from(
                jsValue,
                categoriesMap,
                Some(CategoryInfo(cat, title)),
                combinedCategoryOverride,
                currentPath :+ (CategoryInfo(cat, title), nodeInfo)
              )
            )
          case None =>
            subcategories.flatMap(jsValue =>
              from(
                jsValue,
                categoriesMap,
                currentCategory,
                combinedCategoryOverride,
                currentPath :+ (CategoryInfo(subcategory.get, title), nodeInfo)
              )
            )
        }
    }
  }
}
