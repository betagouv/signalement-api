package repositories.albert

import repositories.PostgresProfile.api._
import slick.lifted.ProvenShape

import java.util.UUID

class AlbertClassificationTable(tag: Tag) extends Table[AlbertClassification](tag, "albert_classification") {

  def reportId        = column[UUID]("report_id", O.PrimaryKey)
  def category        = column[Option[String]]("category")
  def confidenceScore = column[Option[Double]]("confidence_score")
  def explanation     = column[Option[String]]("explanation")
  def summary         = column[Option[String]]("summary")
  def raw             = column[String]("raw")
  def codeConso       = column[Option[String]]("code_conso")

  override def * : ProvenShape[AlbertClassification] = (
    reportId,
    category,
    confidenceScore,
    explanation,
    summary,
    raw,
    codeConso
  ) <> ((AlbertClassification.apply _).tupled, AlbertClassification.unapply)

}

object AlbertClassificationTable {
  val table = TableQuery[AlbertClassificationTable]
}
