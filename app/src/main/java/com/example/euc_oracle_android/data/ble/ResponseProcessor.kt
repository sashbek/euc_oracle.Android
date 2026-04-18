package com.example.euc_oracle_android.data.ble

import android.bluetooth.BluetoothGatt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ResponseProcessor {
    private val readRequests = ConcurrentHashMap<Int, CompletableDeferred<ByteArray>>()
    private val writeRequests = ConcurrentHashMap<UUID, CompletableDeferred<Unit>>()
    private val requestIdGenerator = AtomicInteger(0)

    fun prepareReadRequest(): Int {
        val requestId = requestIdGenerator.incrementAndGet()
        readRequests[requestId] = CompletableDeferred()
        return requestId
    }

    fun prepareWriteRequest(): UUID {
        val requestId = UUID.randomUUID()
        writeRequests[requestId] = CompletableDeferred()
        return requestId
    }

    suspend fun waitForResponse(requestId: Int, timeoutMs: Long = 5000): Result<ByteArray> {
        return try {
            val deferred = readRequests[requestId]
                ?: return Result.failure(IllegalStateException("Request not found"))

            val result = withTimeout(timeoutMs) {
                deferred.await()
            }

            readRequests.remove(requestId)
            Result.success(result)
        } catch (e: Exception) {
            readRequests.remove(requestId)
            Result.failure(e)
        }
    }

    suspend fun waitForWriteComplete(requestId: UUID, timeoutMs: Long = 5000): Result<Unit> {
        return try {
            val deferred = writeRequests[requestId]
                ?: return Result.failure(IllegalStateException("Request not found"))

            withTimeout(timeoutMs) {
                deferred.await()
            }

            writeRequests.remove(requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            writeRequests.remove(requestId)
            Result.failure(e)
        }
    }

    fun processReadResponse(data: ByteArray?) {
        data?.let {
            readRequests.values.firstOrNull()?.complete(it)
        }
    }

    fun processWriteComplete(uuid: UUID, status: Int) {
        writeRequests[uuid]?.let { deferred ->
            if (status == BluetoothGatt.GATT_SUCCESS) {
                deferred.complete(Unit)
            } else {
                deferred.completeExceptionally(Exception("Write failed with status: $status"))
            }
        }
    }

    fun processNotification(data: ByteArray) {
        // Для будущих уведомлений
    }

    fun clear() {
        readRequests.values.forEach { it.cancel() }
        writeRequests.values.forEach { it.cancel() }
        readRequests.clear()
        writeRequests.clear()
    }
}