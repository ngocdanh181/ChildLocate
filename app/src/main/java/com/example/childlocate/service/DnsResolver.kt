package com.example.childlocate.service

import android.content.Context
import android.util.Log
import com.example.childlocate.data.model.BlockedWebsite
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

class DnsResolver {
    private val TAG = "DnsResolver"
    private val blockedIP by lazy { InetAddress.getByName("0.0.0.0") }

    // Cache DNS results để tăng performance
    private val dnsCache = ConcurrentHashMap<String, CacheEntry>()

    private data class CacheEntry(
        val address: InetAddress,
        val timestamp: Long,
        val isBlocked: Boolean
    )

    companion object {
        private const val CACHE_TIMEOUT = 30_000L  // 30 seconds cache
    }

    fun resolve(domain: String, isBlocked: Boolean): InetAddress {
        // Check cache first
        dnsCache[domain]?.let { entry ->
            if (System.currentTimeMillis() - entry.timestamp < CACHE_TIMEOUT) {
                if (entry.isBlocked == isBlocked) {
                    return entry.address
                }
            }
        }

        // If domain is blocked, return blocked IP
        if (isBlocked) {
            dnsCache[domain] = CacheEntry(
                address = blockedIP,
                timestamp = System.currentTimeMillis(),
                isBlocked = true
            )
            return blockedIP
        }

        // Perform actual DNS resolution for non-blocked domains
        return try {
            val address = InetAddress.getByName(domain)
            dnsCache[domain] = CacheEntry(
                address = address,
                timestamp = System.currentTimeMillis(),
                isBlocked = false
            )
            address
        } catch (e: Exception) {
            Log.e(TAG, "DNS resolution failed for $domain", e)
            blockedIP
        }
    }

    // Cleanup expired cache entries periodically
    fun clearExpiredCache() {
        val now = System.currentTimeMillis()
        dnsCache.entries.removeIf { (_, entry) ->
            now - entry.timestamp > CACHE_TIMEOUT
        }
    }
}