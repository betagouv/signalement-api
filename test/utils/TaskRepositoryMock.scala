package utils

import repositories.tasklock.TaskDetails
import repositories.tasklock.TaskRepositoryInterface

import scala.concurrent.Future

class TaskRepositoryMock extends TaskRepositoryInterface {

  override def acquireLock(id: Int): Future[Boolean] = Future.successful(true)

  override def releaseLock(id: Int): Future[Boolean] = Future.successful(true)

  override def create(element: TaskDetails): Future[TaskDetails] = ???

  override def update(id: Int, element: TaskDetails): Future[TaskDetails] = ???

  override def createOrUpdate(element: TaskDetails): Future[TaskDetails] = Future.successful(element)

  override def get(id: Int): Future[Option[TaskDetails]] = ???

  override def delete(id: Int): Future[Int] = ???

  override def list(): Future[List[TaskDetails]] = ???
}
