package repositories.rating

import com.google.inject.ImplementedBy
import models.Rating
import repositories.CRUDRepositoryInterface

@ImplementedBy(classOf[RatingRepository])
trait RatingRepositoryInterface extends CRUDRepositoryInterface[Rating]
