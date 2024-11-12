package repositories.subcategorylabel

import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape
import slick.lifted.Tag

class SubcategoryLabelTable(tag: Tag) extends Table[SubcategoryLabel](tag, "subcategory_labels") {

  def category   = column[String]("category")
  def subcategories   = column[List[String]]("subcategories")
  def categoryLabel = column[String]("category_label")
  def subcategoryLabels = column[List[String]]("subcategory_labels")

  def pk = primaryKey("subcategory_labels_pk_category_subcategories", (category, subcategories))

  type BookmarkData = (
      String,
      List[String],
        String,
      List[String]
  )

  def constructSubcategoryLabels: BookmarkData => SubcategoryLabel = { case (category, subcategories, categoryLabel, subcategoryLabels) =>
    SubcategoryLabel(category, subcategories, categoryLabel, subcategoryLabels)
  }

  def extractSubcategoryLabels: PartialFunction[SubcategoryLabel, BookmarkData] = {
    case SubcategoryLabel(category, subcategories, categoryLabel, subcategoryLabels) =>
      (category, subcategories, categoryLabel, subcategoryLabels)
  }

  override def * : ProvenShape[SubcategoryLabel] =
    (
      category, subcategories, categoryLabel, subcategoryLabels
    ) <> (constructSubcategoryLabels, extractSubcategoryLabels.lift)
}

object SubcategoryLabelTable {
  val table = TableQuery[SubcategoryLabelTable]
}
