package repositories.subcategorylabel

import models.report.Report

case class SubcategoryLabel(
    category: String,
    subcategories: List[String],
    categoryLabelFr: Option[String],
    categoryLabelEn: Option[String],
    subcategoryLabelsFr: Option[List[String]],
    subcategoryLabelsEn: Option[List[String]]
)

object SubcategoryLabel {
  def translateSubcategories(report: Report, subcategoryLabel: Option[SubcategoryLabel]): Report =
    subcategoryLabel
      .flatMap(label => label.subcategoryLabelsFr.orElse(label.subcategoryLabelsEn))
      .fold(report)(labels => report.copy(subcategories = labels))

  def translateSubcategories(subcategories: List[String], subcategoryLabel: Option[SubcategoryLabel]): List[String] =
    subcategoryLabel
      .flatMap(label => label.subcategoryLabelsFr.orElse(label.subcategoryLabelsEn))
      .getOrElse(subcategories)
}
