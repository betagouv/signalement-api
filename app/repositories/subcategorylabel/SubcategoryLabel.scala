package repositories.subcategorylabel

case class SubcategoryLabel(
    category: String,
    subcategories: List[String],
    categoryLabel: String,
    subcategoryLabels: List[String]
)
