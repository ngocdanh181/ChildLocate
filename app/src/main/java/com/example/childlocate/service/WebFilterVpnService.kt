package com.example.childlocate.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.childlocate.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

class WebFilterVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "WebFilterVpnService"
    private lateinit var domainManager: DomainManagerFilter

    companion object {
        private const val MTU = 1500
        private const val BUFFER_SIZE = 2048
        private const val NOTIFICATION_ID = 6789
        private const val CHANNEL_ID = "dns_filter_channel"
        private const val DNS_PORT = 53
    }

    override fun onCreate() {
        super.onCreate()
        domainManager = DomainManagerFilter(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "STOP" -> {
                stopVpnService()
                return START_NOT_STICKY
            }
            else -> {
                if (!isRunning) {
                    startVpnService()
                }
                return START_STICKY
            }
        }
    }

    private fun startVpnService() {
        try {
            // Start as foreground service
            startForeground(NOTIFICATION_ID, createNotification())

            // Get childId
            val childId = getChildId() ?: run {
                Log.e(TAG, "No child ID found")
                stopSelf()
                return
            }

            // Start monitoring blocked domains
            domainManager.startMonitoring(childId) { keywords ->
                Log.d(TAG, "Updated blocked keywords: $keywords")
            }

            // Setup VPN
            setupVpn()

            // Start packet processing
            startPacketProcessing()

            isRunning = true
            Log.d(TAG, "VPN Service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VPN", e)
            stopSelf()
        }
    }

    private fun setupVpn() {
        try {
            vpnInterface = Builder().apply {
                setMtu(MTU)
                addAddress("10.0.0.2", 32)

                // Add DNS servers
                addDnsServer("8.8.8.8")

                // Chỉ route DNS traffic
                //addRoute(DNS_SERVER, 32)  // Route chỉ đến DNS server

                // Allow other traffic bypass VPN
                allowBypass()

                setBlocking(false)
                setSession("DNS Filter")
            }.establish()
        } catch (e: Exception) {
            Log.e(TAG, "VPN setup failed", e)
            throw e
        }
    }

    private fun startPacketProcessing() {
        serviceScope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(vpnInterface?.fileDescriptor)
            val outputStream = FileOutputStream(vpnInterface?.fileDescriptor)
            val packet = ByteArray(BUFFER_SIZE)

            while (isRunning) {
                try {
                    val length = inputStream.read(packet)
                    if (length <= 0) continue

                    // Check if this is a DNS packet
                    if (isDnsPacket(packet)) {
                        handleDnsPacket(packet, length, outputStream)
                    } else {
                        // Forward non-DNS packets immediately
                        outputStream.write(packet, 0, length)
                        outputStream.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet", e)
                }
            }
        }
    }

    private fun isDnsPacket(packet: ByteArray): Boolean {
        return try {
            // Check UDP destination port 53 (DNS)
            packet[22].toInt() == 0 && packet[23].toInt() == DNS_PORT
        } catch (e: Exception) {
            false
        }
    }

    private fun handleDnsPacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        try {
            val domain = extractDomain(packet)
            if (domain == null) {
                output.write(packet, 0, length)
                return
            }

            Log.d(TAG, "DNS query for domain: $domain")

            if (domainManager.containsBlockedKeyword(domain)) {
                Log.d(TAG, "Blocking domain: $domain")
                val response = createDnsBlockResponse(packet)
                output.write(response)
            } else {
                output.write(packet, 0, length)
            }
            output.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling DNS packet", e)
            output.write(packet, 0, length)
            output.flush()
        }
    }

    private fun extractDomain(packet: ByteArray): String? {
        try {
            var position = 28  // Skip IP(20) + UDP(8) headers
            position += 12     // Skip DNS header

            val domain = StringBuilder()
            var labelLength = packet[position++].toInt() and 0xFF

            while (labelLength > 0) {
                repeat(labelLength) {
                    domain.append(packet[position++].toChar())
                }
                labelLength = packet[position++].toInt() and 0xFF
                if (labelLength > 0) domain.append('.')
            }

            return domain.toString().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain", e)
            return null
        }
    }

    private fun createDnsBlockResponse(request: ByteArray): ByteArray {
        val response = ByteArray(request.size)
        System.arraycopy(request, 0, response, 0, request.size)

        // Set response bit and no error
        response[2] = 0x81.toByte()
        response[3] = 0x80.toByte()

        // Set answer count to 1
        response[6] = 0x00
        response[7] = 0x01

        // Add answer section with 0.0.0.0
        val answerOffset = response.size - 16
        response[answerOffset] = 0xC0.toByte()    // Name pointer
        response[answerOffset + 1] = 0x0C.toByte()
        response[answerOffset + 3] = 0x01         // Type A
        response[answerOffset + 5] = 0x01         // Class IN
        response[answerOffset + 10] = 30.toByte() // TTL 30 seconds
        response[answerOffset + 11] = 0x04        // Data length
        // IP 0.0.0.0
        response[answerOffset + 12] = 0x00
        response[answerOffset + 13] = 0x00
        response[answerOffset + 14] = 0x00
        response[answerOffset + 15] = 0x00

        return response
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DNS Filter Active")
            .setContentText("Filtering web content")
            .setSmallIcon(R.drawable.ic_vpn)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "DNS Filter Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running DNS filtering service"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun getChildId(): String? {
        return applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            .getString("childId", null)
    }

    private fun stopVpnService() {
        isRunning = false
        serviceScope.cancel()
        domainManager.stopMonitoring()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVpnService()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}


/*
class WebFilterVpnService : VpnService() {
    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val TAG = "WebFilterVpnService"

    private lateinit var dnsResolver: DnsResolver
    private lateinit var domainManager: DomainManagerFilter
    private val dnsCache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val response: ByteArray,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired() = System.currentTimeMillis() - timestamp > 30000 // 30s cache
    }

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "vpn_channel"
        private const val NOTIFICATION_ID = 5236
        private const val BUFFER_SIZE = 4096
        private const val VPN_MTU = 1500
        private const val DNS_PORT = 53
        private const val DNS_SERVER = "8.8.8.8"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        initializeComponents()
    }

    private fun initializeComponents() {
        dnsResolver = DnsResolver()
        domainManager = DomainManagerFilter(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")
        return when (intent?.action) {
            "STOP" -> {
                stopVpn()
                stopSelf()
                START_NOT_STICKY
            }
            else -> {
                setupVpnAndStart()
                START_STICKY
            }
        }
    }

    private fun setupVpnAndStart() {
        try {
            setupVpnInterface()
            startDomainMonitoring()
            startForeground(NOTIFICATION_ID, createNotification())
            startPacketProcessing()
            isRunning = true
            Log.d(TAG, "VPN setup completed and started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup VPN", e)
            stopSelf()
        }
    }

    private fun setupVpnInterface() {
        try {
            vpnInterface = Builder().apply {
                setMtu(VPN_MTU)
                addAddress("10.0.0.2", 32)

                // Add DNS servers
                addDnsServer(DNS_SERVER)

                // Chỉ cho phép browser apps
                addAllowedApplication("com.android.chrome")
                addAllowedApplication("com.google.android.browser")

                // QUAN TRỌNG: Route tất cả traffic
                addRoute("0.0.0.0", 0)  // Route all IPv4 traffic

                setBlocking(false)
                setSession("Web Filter VPN")
            }.establish()

            Log.d(TAG, "VPN interface established")
        } catch (e: Exception) {
            Log.e(TAG, "VPN setup failed", e)
            throw e
        }
    }

    private fun startDomainMonitoring() {
        getChildId()?.let { childId ->
            domainManager.startMonitoring(childId) { keywords ->
                Log.d(TAG, "Updated blocked keywords: ${keywords.size}")
            }
        } ?: run {
            Log.e(TAG, "No child ID found")
            stopSelf()
        }
    }

    private fun startPacketProcessing() {
        serviceScope.launch(Dispatchers.IO) {
            val packet = ByteArray(BUFFER_SIZE)
            val vpnInput = FileInputStream(vpnInterface?.fileDescriptor)
            val vpnOutput = FileOutputStream(vpnInterface?.fileDescriptor)

            while (isRunning) {
                try {
                    val length = vpnInput.read(packet)
                    if (length <= 0) continue

                    if (length >= 28 && isDnsPacket(packet)) {
                        Log.d(TAG, "Processing DNS packet")
                        handleDnsPacket(packet, length, vpnOutput)
                    } else {
                        Log.d(TAG, "Forwarding non-DNS packet")
                        vpnOutput.write(packet, 0, length)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing packet", e)
                }
            }
        }
    }

    private fun isDnsPacket(packet: ByteArray): Boolean {
        return try {
            packet[22].toInt() == 0 && packet[23].toInt() == DNS_PORT
        } catch (e: Exception) {
            false
        }
    }

    private fun handleDnsPacket(packet: ByteArray, length: Int, output: FileOutputStream) {
        try {
            val domain = extractDomain(packet)
            Log.d(TAG," gia tri domain la $domain")
            if (domain == null) {
                output.write(packet, 0, length)
                return
            }

            if (!domainManager.containsBlockedKeyword(domain)) {
                output.write(packet, 0, length) // Chuyển tiếp gói DNS không bị block
                Log.d(TAG," gia tri la ${domainManager.containsBlockedKeyword(domain)}")
                return
            }
            // Only log blocked domains
            if (domainManager.containsBlockedKeyword(domain)) {
                Log.d(TAG, "🚫 Blocking domain: $domain")
                val response = createDnsResponse(packet, domain)
                dnsCache[domain] = CacheEntry(response)
                output.write(response)
            } else {
                // Quietly forward non-blocked domains
                output.write(packet, 0, length)
            }
        } catch (e: Exception) {
            output.write(packet, 0, length)
        }
    }

    private fun extractDomain(packet: ByteArray): String? {
        try {
            // Skip IP header (20 bytes) and UDP header (8 bytes)
            var position = 28
            // Validate packet has enough bytes for DNS header (12 bytes)
            if (packet.size < position + 12) {
                Log.d(TAG, "Packet too small for DNS header: ${packet.size} bytes")
                return null
            }
            // Skip DNS header
            position += 12

            // Parse domain using DNS message compression
            val domain = StringBuilder()
            var labelLength = packet[position++].toInt() and 0xFF
            var jumps = 0  // Prevent infinite loops from malformed compression
            var totalLength = 0  // Track total domain length

            // Process domain labels
            while (labelLength > 0 && jumps < 5 && totalLength < 255) {  // DNS limits
                // Handle DNS name compression
                if ((labelLength and 0xC0) == 0xC0) {  // Top 2 bits set = pointer
                    if (position >= packet.size) {
                        Log.d(TAG, "Invalid compression pointer position")
                        return null
                    }
                    // Calculate pointer position
                    position = ((labelLength and 0x3F) shl 8) + (packet[position].toInt() and 0xFF)
                    labelLength = packet[position++].toInt() and 0xFF
                    jumps++
                    continue
                }
                // Validate label length
                if (position + labelLength >= packet.size) {
                    Log.d(TAG, "Label exceeds packet bounds")
                    return null
                }
                // Read label characters
                repeat(labelLength) {
                    val c = packet[position++].toInt() and 0xFF
                    if (c in 32..126) {  // Valid ASCII characters only
                        domain.append(c.toChar())
                    } else {
                        Log.d(TAG, "Invalid character in domain name: $c")
                        return null
                    }
                }

                totalLength += labelLength

                // Read next label length
                if (position >= packet.size) break
                labelLength = packet[position++].toInt() and 0xFF
                if (labelLength > 0) domain.append('.')
            }
            val extractedDomain = domain.toString()
            if (extractedDomain.isEmpty() || totalLength > 255) {
                Log.d(TAG, "Invalid domain length: $totalLength")
                return null
            }
            Log.d(TAG, "Successfully extracted domain: $extractedDomain")
            return extractedDomain
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting domain", e)
            return null
        }
    }

    private fun createDnsResponse(request: ByteArray, domain: String): ByteArray {
        try {
            // Find size of question section (includes domain name)
            val questionSize = findQuestionSectionSize(request)

            // Total response size = DNS header (12) + Question + Answer (16)
            val responseSize = 12 + questionSize + 16
            val response = ByteArray(responseSize)

            // 1. Copy Transaction ID (first 2 bytes)
            System.arraycopy(request, 0, response, 0, 2)

            // 2. Set DNS header flags
            // Byte 2: Response + Recursion Desired + Not truncated
            // 1000 0101 = 0x85 (Response bit + Recursion Desired)
            response[2] = 0x85.toByte()

            // Byte 3: Recursion Available + No error code
            // 1000 0000 = 0x80
            response[3] = 0x80.toByte()

            // 3. Set counts
            response[4] = 0x00  // Questions = 1
            response[5] = 0x01
            response[6] = 0x00  // Answers = 1
            response[7] = 0x01
            response[8] = 0x00  // Authority RRs = 0
            response[9] = 0x00
            response[10] = 0x00 // Additional RRs = 0
            response[11] = 0x00

            // 4. Copy question section (includes original domain name)
            System.arraycopy(request, 12, response, 12, questionSize)
            var pos = 12 + questionSize

            // 5. Add answer section
            // Name pointer to question (compression)
            response[pos++] = 0xC0.toByte()  // Pointer marker
            response[pos++] = 0x0C.toByte()  // Offset to question

            // Type A record (IPv4)
            response[pos++] = 0x00
            response[pos++] = 0x01

            // Class IN (Internet)
            response[pos++] = 0x00
            response[pos++] = 0x01

            // TTL: 30 seconds (prevent long caching)
            response[pos++] = 0x00
            response[pos++] = 0x00
            response[pos++] = 0x00
            response[pos++] = 0x1E

            // Data length (4 bytes for IPv4)
            response[pos++] = 0x00
            response[pos++] = 0x04

            // IP address (0.0.0.0 for blocked domains)
            response[pos++] = 0x00
            response[pos++] = 0x00
            response[pos++] = 0x00
            response[pos++] = 0x00

            Log.d(TAG, "Created DNS response for blocked domain: $domain")
            return response

        } catch (e: Exception) {
            Log.e(TAG, "Error creating DNS response for $domain", e)
            return ByteArray(0)
        }
    }

    private fun findQuestionSectionSize(request: ByteArray): Int {
        var pos = 12  // Skip DNS header

        // Read domain name labels until we hit a zero length label
        while (pos < request.size) {
            val len = request[pos].toInt() and 0xFF
            if (len == 0) break  // End of domain name
            pos += len + 1  // Skip label and length byte
        }

        return (pos - 12) + 5  // Add 5 for null terminator (1) + QTYPE(2) + QCLASS(2)
    }

    private fun getChildId(): String? {
        return applicationContext.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
            .getString("childId", null)?.also {
                Log.d(TAG, "Child ID: $it")
            }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainChildActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Web Filter Active")
            .setContentText("Web filtering is running")
            .setSmallIcon(R.drawable.ic_vpn)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Web Filter VPN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running VPN service for web filtering"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun stopVpn() {
        Log.d(TAG, "Stopping VPN service")
        isRunning = false
        cleanup()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        cleanup()
    }

    private fun cleanup() {
        isRunning = false
        serviceScope.cancel()
        domainManager.stopMonitoring()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(true)
        Log.d(TAG, "Cleanup completed")
    }
}*/