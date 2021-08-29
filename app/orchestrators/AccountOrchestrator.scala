package orchestrators

import cats.data.OptionT
import models.{User, UserUpdate}
import repositories.UserRepository

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AccountOrchestrator @Inject() (
    userRepository: UserRepository
)(implicit val executionContext: ExecutionContext) {

  def patchUser(userId: UUID, userUpdate: UserUpdate): Future[Option[User]] = {
    (for {
      user <- OptionT(userRepository.findById(userId))
      updatedUser = userUpdate.mergeInto(user)
      _ <- OptionT.liftF(userRepository.update(updatedUser))
    } yield updatedUser).value
  }
}
