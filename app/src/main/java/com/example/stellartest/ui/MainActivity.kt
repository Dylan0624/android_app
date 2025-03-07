package com.example.stellartest

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime

class MainActivity : Activity() {
    private val isRunning = AtomicBoolean(false)
    private var udpJob: Job? = null
    private var processingJob: Job? = null
    private val gcHandler = Handler(Looper.getMainLooper())
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView

    // 使用並發隊列來處理數據包
    private val packetQueue = ConcurrentLinkedQueue<ByteArray>()
    // 使用原子計數器來追蹤統計
    private val totalBytesReceived = AtomicLong(0)
    private val totalPacketsReceived = AtomicLong(0)
    private val bytesInLastSecond = AtomicLong(0)

    // 接收緩衝區大小 - 盡可能大
    private val SOCKET_BUFFER_SIZE = 64 * 1024 * 1024 // 64MB
    private val PACKET_BUFFER_SIZE = 65535 // 最大UDP包大小

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val textView = findViewById<TextView>(R.id.textView)
        val speedTextView = findViewById<TextView>(R.id.speedTextView)
        val udpButton = findViewById<Button>(R.id.udpButton)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)

        udpButton.setOnClickListener {
            if (!isRunning.get()) {
                isRunning.set(true)
                log("UDP", "啟動 UDP 監聽")

                // 啟動接收和處理作業
                udpJob = startUdpReceiver(speedTextView)
                processingJob = startPacketProcessor(textView)

                udpButton.text = "停止 UDP 監聽"
            } else {
                isRunning.set(false)
                log("UDP", "停止 UDP 監聽")
                udpJob?.cancel()
                processingJob?.cancel()

                udpButton.text = "啟動 UDP 監聽"
            }
        }

        // 定期觸發垃圾回收，防止OOM
        startPeriodicGC()
    }

    private fun startPeriodicGC() {
        gcHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isRunning.get()) {
                    System.gc()
                    log("GC", "觸發垃圾回收")
                    gcHandler.postDelayed(this, 5000) // 每5秒
                }
            }
        }, 5000)
    }

    private fun startUdpReceiver(speedTextView: TextView): Job {
        return CoroutineScope(Dispatchers.IO).launch {
            try {
                val port = 7000
                log("UDP", "手機作為主機，開始監聽 UDP 端口 $port")

                // 創建並配置 DatagramSocket
                val socket = DatagramSocket(port).apply {
                    reuseAddress = true
                    broadcast = true
                    try {
                        receiveBufferSize = SOCKET_BUFFER_SIZE
                        log("UDP", "已設置 Socket 接收緩衝區大小: ${receiveBufferSize} bytes")
                    } catch (e: Exception) {
                        log("UDP", "警告: 無法設置請求的緩衝區大小: ${e.message}")
                        log("UDP", "當前緩衝區大小: ${receiveBufferSize} bytes")
                    }
                }

                // 啟動速率監控協程
                val speedMonitorJob = launch {
                    var lastUpdateTime = System.nanoTime()

                    while (isRunning.get()) {
                        delay(1000) // 每秒更新一次

                        val currentTime = System.nanoTime()
                        val elapsedSeconds = (currentTime - lastUpdateTime) / 1_000_000_000.0
                        val bytesReceived = bytesInLastSecond.getAndSet(0)

                        val speedMBps = bytesReceived / (1024.0 * 1024.0) / elapsedSeconds
                        val totalMB = totalBytesReceived.get() / (1024.0 * 1024.0)
                        val packets = totalPacketsReceived.get()

                        withContext(Dispatchers.Main) {
                            speedTextView.text = "速率: %.2f MB/s | 總計: %.2f MB | 封包: %d | 佇列: %d"
                                .format(speedMBps, totalMB, packets, packetQueue.size)
                        }

                        lastUpdateTime = currentTime
                    }
                }

                // 主接收循環
                try {
                    while (isRunning.get()) {
                        // 每次創建新的緩衝區，避免覆蓋
                        val buffer = ByteArray(PACKET_BUFFER_SIZE)
                        val receivePacket = DatagramPacket(buffer, buffer.size)

                        try {
                            socket.receive(receivePacket)

                            // 只複製實際數據長度
                            val dataLength = receivePacket.length
                            val actualData = buffer.copyOfRange(0, dataLength)

                            // 更新統計數據
                            totalBytesReceived.addAndGet(dataLength.toLong())
                            bytesInLastSecond.addAndGet(dataLength.toLong())
                            totalPacketsReceived.incrementAndGet()

                            // 將數據放入隊列
                            packetQueue.offer(actualData)

                            // 檢查隊列大小，防止OOM
                            if (packetQueue.size > 10000) {
                                log("UDP", "警告: 隊列過大 (${packetQueue.size})，丟棄舊數據")
                                while (packetQueue.size > 5000) {
                                    packetQueue.poll()
                                }
                            }
                        } catch (e: Exception) {
                            if (isRunning.get()) {
                                log("UDP", "接收數據時出錯: ${e.message}")
                            }
                        }
                    }
                } finally {
                    speedMonitorJob.cancel()
                    socket.close()
                    log("UDP", "關閉 UDP 監聽，共接收 ${totalPacketsReceived.get()} 個封包，總計 ${totalBytesReceived.get() / (1024 * 1024)} MB")
                }
            } catch (e: Exception) {
                log("UDP", "UDP 監聽失敗: ${e.message}")
            }
        }
    }

    private fun startPacketProcessor(textView: TextView): Job {
        return CoroutineScope(Dispatchers.Default).launch {
            log("處理", "啟動數據包處理協程")

            try {
                while (isRunning.get()) {
                    // 批次處理隊列中的數據，減少線程切換
                    val packetsToProcess = mutableListOf<ByteArray>()

                    // 從隊列中最多取出100個數據包進行批處理
                    for (i in 0 until 100) {
                        val packet = packetQueue.poll() ?: break
                        packetsToProcess.add(packet)
                    }

                    if (packetsToProcess.isNotEmpty()) {
                        // 在這裡可以對取出的數據包進行進一步處理
                        // 例如解析、存儲或顯示

                        // 更新UI（僅顯示最後一個包的大小作為示例）
                        val lastPacketSize = packetsToProcess.last().size
                        withContext(Dispatchers.Main) {
                            textView.text = "最新數據包大小: $lastPacketSize bytes"
                        }
                    } else {
                        // 如果隊列為空，稍微暫停一下，避免CPU過載
                        delay(10)
                    }
                }
            } catch (e: Exception) {
                log("處理", "數據處理出錯: ${e.message}")
            }

            log("處理", "數據包處理協程已停止")
        }
    }

    private fun log(tag: String, message: String) {
        Log.d(tag, message)
        runOnUiThread {
            logTextView.append("[$tag] $message\n")

            // 限制日誌量，防止記憶體洩漏
            if (logTextView.lineCount > 100) {
                val text = logTextView.text.toString()
                val lines = text.split("\n")
                logTextView.text = lines.takeLast(50).joinToString("\n")
            }

            logScrollView.post { logScrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning.set(false)
        udpJob?.cancel()
        processingJob?.cancel()
        gcHandler.removeCallbacksAndMessages(null)
    }
}