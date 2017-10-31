import kotlinx.cinterop.*
import platform.posix.*
import konan.worker.*
import kotlin.*

val prefixBuffer = kotlin.text.toUtf8Array("Welcome!\n", 0, 9)

fun handleConnection(commFd: Int): Int {
    val buffer = ByteArray(1024)

    try {
        println("handleConnection 1 - $commFd")
        buffer.usePinned { pinned ->
            println("handleConnection 2")
            while (true) {
                println("handleConnection loop")

                val length = recv(commFd, pinned.addressOf(0), buffer.size.signExtend(), 0).toInt()
                  .ensureUnixCallResult("read") { it >= 0 }

                println("handleConnection read")
                if (length == 0) {
                    break
                }

                send(commFd, prefixBuffer.refTo(0), prefixBuffer.size.signExtend(), 0)
                  .ensureUnixCallResult("write") { it >= 0 }
                send(commFd, pinned.addressOf(0), length.signExtend(), 0)
                  .ensureUnixCallResult("write") { it >= 0 }
                println("handleConnection send")
            }
        }
    } catch (e: Error) {
        println("Error in handleConnection: ${e.message}")
        e.printStackTrace()
    }

    return 0
}

fun main(args: Array<String>) {
    println("platform testing xxx")

    val port = 4567.toShort()

    init_sockets()

    memScoped {

        val serverAddr = alloc<sockaddr_in>()

        try {
            val listenFd = socket(AF_INET, SOCK_STREAM, 0)
              .ensureUnixCallResult("socket") { it >= 0 }

            with(serverAddr) {
                memset(this.ptr, 0, sockaddr_in.size)
                sin_family = AF_INET.narrow()
                sin_port = posix_htons(port)
            }

            bind(listenFd, serverAddr.ptr.reinterpret(), sockaddr_in.size.toInt())
              .ensureUnixCallResult("bind") { it == 0 }

            listen(listenFd, 10)
              .ensureUnixCallResult("listen") { it == 0 }

            while (true) {
                val commFd = accept(listenFd, null, null)
                  .ensureUnixCallResult("accept") { it >= 0 }

                send(commFd, prefixBuffer.refTo(0), prefixBuffer.size.signExtend(), 0)
                  .ensureUnixCallResult("write") { it >= 0 }

                val worker = startWorker()

                val future = worker.schedule(TransferMode.CHECKED, {
                    commFd
                }, { fd ->
                    println("call handleConnection -> $fd")

                    handleConnection(fd)
                })

                //future.consume {
                //    worker.requestTermination().consume { _ -> }
                //}
            }
        } catch (e: Error) {
            println(e.message)
            e.printStackTrace()
        }
    }
}

inline fun Int.ensureUnixCallResult(op: String, predicate: (Int) -> Boolean): Int {
    if (!predicate(this)) {
        throw Error("$op: ${strerror(posix_errno())!!.toKString()}")
    }
    return this
}

inline fun Long.ensureUnixCallResult(op: String, predicate: (Long) -> Boolean): Long {
    if (!predicate(this)) {
        throw Error("$op: ${strerror(posix_errno())!!.toKString()}")
    }
    return this
}
