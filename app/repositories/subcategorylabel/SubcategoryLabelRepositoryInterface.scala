package repositories.subcategorylabel

import scala.concurrent.Future

trait SubcategoryLabelRepositoryInterface {

  def createOrUpdateAll(elements: List[SubcategoryLabel]): Future[Unit]
  def get(category: String, subcategories: List[String]): Future[Option[SubcategoryLabel]]
  def list(): Future[List[SubcategoryLabel]]
}
