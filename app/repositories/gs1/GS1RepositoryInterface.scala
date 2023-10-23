package repositories.gs1

import models.gs1.GS1Product
import repositories.TypedCRUDRepositoryInterface

trait GS1RepositoryInterface extends TypedCRUDRepositoryInterface[GS1Product, String]
