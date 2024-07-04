package utils.mdc

import java.util.concurrent.TimeUnit
import com.typesafe.config.Config
import org.apache.pekko.dispatch.Dispatcher
import org.apache.pekko.dispatch.DispatcherPrerequisites
import org.apache.pekko.dispatch.ExecutorServiceFactoryProvider
import org.apache.pekko.dispatch.MessageDispatcher
import org.apache.pekko.dispatch.MessageDispatcherConfigurator
import org.slf4j.MDC

import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

/** Configurator for a MDC propagating dispatcher.
  *
  * To use it, configure play like this:
  * {{{
  * play {
  *   pekko {
  *     actor {
  *       default-dispatcher = {
  *         type = "utils.mdc.MDCPropagatingDispatcherConfigurator"
  *       }
  *     }
  *   }
  * }
  * }}}
  *
  * Credits to James Roper for the
  * [[https://github.com/jroper/thread-local-context-propagation/ initial implementation]]
  */
class MDCPropagatingDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
    extends MessageDispatcherConfigurator(config, prerequisites) {

//  println(s"------------------ config = ${config} ------------------")

  private val instance = new MDCPropagatingDispatcher(
    this,
//    config.getString("id"),
    "toto",
    config.getInt("throughput"),
    FiniteDuration(config.getDuration("throughput-deadline-time", TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS),
    configureExecutor(),
    FiniteDuration(config.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  )

  override def dispatcher(): MessageDispatcher = instance
}

/** A MDC propagating dispatcher.
  *
  * This dispatcher propagates the MDC current request context if it's set when it's executed.
  */
class MDCPropagatingDispatcher(
    _configurator: MessageDispatcherConfigurator,
    id: String,
    throughput: Int,
    throughputDeadlineTime: Duration,
    executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
    shutdownTimeout: FiniteDuration
) extends Dispatcher(
      _configurator,
      id,
      throughput,
      throughputDeadlineTime,
      executorServiceFactoryProvider,
      shutdownTimeout
    ) {

  self =>

  override def execute(r: Runnable): Unit = {
    val uuid = UUID.randomUUID().toString
    // capture the MDC
    val mdcContext       = MDC.getCopyOfContextMap
    val submittingThread = Thread.currentThread.getName
    println(s" [$uuid] Submitting task with MDC context: $mdcContext from thread: $submittingThread")

    super.execute(new Runnable {
      def run() = {
        // backup the callee MDC context
        val oldMDCContext   = MDC.getCopyOfContextMap
        val executingThread = Thread.currentThread.getName
        println(s" [$uuid]Saving old MDC context: $oldMDCContext on thread $executingThread ")

        // Run the runnable with the captured context
        setContextMap(mdcContext)
        try {
          println(s" [$uuid]BEGIN EXECUTION with : $mdcContext on thread $executingThread ")
          r.run()
          println(s" [$uuid]END EXECUTION with : $mdcContext on thread $executingThread ")
        } finally
          // restore the callee MDC context
          setContextMap(oldMDCContext)
        println(s" [$uuid]Restoring old MDC context: $oldMDCContext on thread $executingThread ")
      }
    })
  }

  private[this] def setContextMap(context: java.util.Map[String, String]) {
    if (context == null) {
      MDC.clear()
    } else {
      MDC.setContextMap(context)
    }
  }
}
