package repositories.gs1

import repositories.PostgresProfile.api._

import java.util.UUID

class GS1NetContentTable(tag: Tag) extends Table[GS1NetContentRow](tag, "gs1_net_content") {

  def unitCode     = column[Option[String]]("unit_code")
  def quantity     = column[Option[String]]("quantity")
  def gs1ProductId = column[UUID]("gs1_product_id")
  def fkGs1Product = foreignKey("fk_gs1_product", gs1ProductId, GS1ProductTable.table)(
    _.id,
    onUpdate = ForeignKeyAction.Cascade,
    onDelete = ForeignKeyAction.Cascade
  )

  override def * = (
    unitCode,
    quantity,
    gs1ProductId
  ) <> ((GS1NetContentRow.apply _).tupled, GS1NetContentRow.unapply)
}

object GS1NetContentTable {
  val table = TableQuery[GS1NetContentTable]
}
