package repositories.subcategorylabel

import scala.concurrent.Future

trait SubcategoryLabelRepositoryInterface {

  def createOrUpdate(element: SubcategoryLabel): Future[SubcategoryLabel]
  def get(category: String, subcategories: List[String]): Future[Option[SubcategoryLabel]]
}
