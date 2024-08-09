package models.report

case class PreFilter(
    category: Option[ReportCategory],
    tags: List[ReportTag]
)

object PreFilter {
  val NoFilter: PreFilter = PreFilter(None, List.empty)
  val DGALFilter: PreFilter =
    PreFilter(
      Some(ReportCategory.IntoxicationAlimentaire),
      List(ReportTag.AlimentationMaterielAnimaux, ReportTag.Hygiene, ReportTag.ProduitAlimentaire)
    )
}
