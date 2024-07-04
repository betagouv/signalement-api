package utils.mdc

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContextExecutor
import org.slf4j.MDC

class SignalConsoExecutionContext(ec: ExecutionContext) extends ExecutionContextExecutor {
  override def execute(runnable: Runnable): Unit = {

    val context = MDC.getCopyOfContextMap
    ec.execute(new Runnable {
      override def run(): Unit = {
        if (context != null) MDC.setContextMap(context)
        try
          runnable.run()
        finally
          MDC.clear()
      }
    })
  }

  override def reportFailure(cause: Throwable): Unit = ec.reportFailure(cause)
}

object SignalConsoExecutionContext {
  def apply(ec: ExecutionContext): ExecutionContextExecutor =
    new SignalConsoExecutionContext(ec)
}
