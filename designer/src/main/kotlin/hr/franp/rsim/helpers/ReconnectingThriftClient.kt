package hr.franp.rsim.helpers

import org.apache.thrift.TServiceClient
import org.apache.thrift.transport.TTransport
import org.apache.thrift.transport.TTransportException
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*

/**
 * Helper proxy class. Attempts to call method on proxy object wrapped in try/catch. If it fails, it attempts a
 * reconnect and tries the method again.
 */
class ReconnectingClientProxy<T : TServiceClient>(private val baseClient: T, private val maxRetries: Int, private val timeBetweenRetries: Long) : InvocationHandler {
    private val LOG = LoggerFactory.getLogger(ReconnectingThriftClient::class.java)

    /**
     * List of causes which suggest a restart might fix things (defined as constants in [org.apache.thrift.transport.TTransportException]).
     */
    private val RESTARTABLE_CAUSES = HashSet(Arrays.asList(
        TTransportException.END_OF_FILE,
        TTransportException.TIMED_OUT,
        TTransportException.UNKNOWN
    ))

    @Throws(Throwable::class)
    override fun invoke(proxy: Any, method: Method, args: Array<Any>?): Any? {
        val methodArguments = args ?: arrayOfNulls<Any>(0)
        try {
            return method.invoke(baseClient, *methodArguments)
        } catch (e: InvocationTargetException) {
            if (e.targetException is TTransportException) {
                val cause = e.targetException as TTransportException

                if (RESTARTABLE_CAUSES.contains(cause.type)) {
                    reconnectOrThrowException(baseClient.inputProtocol.transport, maxRetries, timeBetweenRetries)
                    return method.invoke(baseClient, *methodArguments)
                }
            }

            throw e
        }

    }

    @Throws(TTransportException::class)
    private fun reconnectOrThrowException(transport: TTransport, maxRetries: Int, timeBetweenRetries: Long) {
        var errors = 0
        transport.close()

        while (errors < maxRetries) {
            try {
                LOG.info("Attempting to reconnect... $errors/$maxRetries")
                transport.open()
                LOG.info("Reconnection successful")
                break
            } catch (e: TTransportException) {
                LOG.error("Error while reconnecting:", e)
                errors++

                if (errors < maxRetries) {
                    try {
                        LOG.info("Sleeping for {} milliseconds before retrying", timeBetweenRetries)
                        Thread.sleep(timeBetweenRetries)
                    } catch (e2: InterruptedException) {
                        throw RuntimeException(e)
                    }

                }
            }

        }

        if (errors >= maxRetries) {
            throw TTransportException("Failed to reconnect")
        }
    }
}

/**
 * @param numRetries         the maximum number of times to try reconnecting before giving up and throwing an
 * *                           exception
 * *
 * @param timeBetweenRetries the number of milliseconds to wait in between reconnection attempts.
 */
data class ReconnectingThriftClientOptions(var numRetries: Int = 5, var timeBetweenRetries: Long = 10000L) {

    fun withNumRetries(numRetries: Int): ReconnectingThriftClientOptions {
        return this.copy(
            numRetries = numRetries
        )
    }

    fun withTimeBetweenRetries(timeBetweenRetries: Long): ReconnectingThriftClientOptions {
        return this.copy(
            timeBetweenRetries = timeBetweenRetries
        )
    }

}

object ReconnectingThriftClient {

    inline fun <C : TServiceClient, reified T : Any> wrap(value: C) = wrap(value, T::class.java)

    fun <C : TServiceClient, T> wrap(value: C, clazz: Class<T>, options: ReconnectingThriftClientOptions = ReconnectingThriftClientOptions()): T {
        val loader = clazz.classLoader
        val interfaces = arrayOf(clazz)

        return clazz.cast(Proxy.newProxyInstance(
            loader,
            interfaces,
            ReconnectingClientProxy(
                value,
                options.numRetries,
                options.timeBetweenRetries
            )
        ))
    }

}