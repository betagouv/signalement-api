package repositories.subcategorylabel

import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape
import slick.lifted.Tag

class SubcategoryLabelTable(tag: Tag) extends Table[SubcategoryLabel](tag, "subcategory_labels") {

  def category            = column[String]("category")
  def subcategories       = column[List[String]]("subcategories")
  def categoryLabelFr     = column[Option[String]]("category_label_fr")
  def categoryLabelEn     = column[Option[String]]("category_label_en")
  def subcategoryLabelsFr = column[Option[List[String]]]("subcategory_labels_fr")
  def subcategoryLabelsEn = column[Option[List[String]]]("subcategory_labels_en")

  def pk = primaryKey("subcategory_labels_pk_category_subcategories", (category, subcategories))

  type BookmarkData = (
      String,
      List[String],
      Option[String],
      Option[String],
      Option[List[String]],
      Option[List[String]]
  )

  def constructSubcategoryLabels: BookmarkData => SubcategoryLabel = {
    case (category, subcategories, categoryLabelFr, categoryLabelEn, subcategoryLabelsFr, subcategoryLabelsEn) =>
      SubcategoryLabel(
        category,
        subcategories,
        categoryLabelFr,
        categoryLabelEn,
        subcategoryLabelsFr,
        subcategoryLabelsEn
      )
  }

  def extractSubcategoryLabels: PartialFunction[SubcategoryLabel, BookmarkData] = {
    case SubcategoryLabel(
          category,
          subcategories,
          categoryLabelFr,
          categoryLabelEn,
          subcategoryLabelsFr,
          subcategoryLabelsEn
        ) =>
      (category, subcategories, categoryLabelFr, categoryLabelEn, subcategoryLabelsFr, subcategoryLabelsEn)
  }

  override def * : ProvenShape[SubcategoryLabel] =
    (
      category,
      subcategories,
      categoryLabelFr,
      categoryLabelEn,
      subcategoryLabelsFr,
      subcategoryLabelsEn
    ) <> (constructSubcategoryLabels, extractSubcategoryLabels.lift)
}

object SubcategoryLabelTable {
  val table = TableQuery[SubcategoryLabelTable]
}
