package com.example.childlocate.service

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener



class DomainManagerFilter(private val context: Context) {
    private val database = FirebaseDatabase.getInstance()
    private val domainsRef = database.getReference("web_filter")
    private var valueEventListener: ValueEventListener? = null
    private val TAG = "DomainManagerFilter"

    // Sử dụng companion object để shared state
    companion object {
        @Volatile
        private var blockedKeywords = setOf<String>()
    }

    fun startMonitoring(childId: String, onDomainsUpdated: (Set<String>) -> Unit) {
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val keywords = mutableSetOf<String>()
                    snapshot.child(childId).child("websites").children.forEach { domainSnapshot ->
                        val keyword = domainSnapshot.child("domain").getValue(String::class.java)
                        if (!keyword.isNullOrBlank()) {
                            keywords.add(keyword.lowercase())
                        }
                    }
                    // Update shared state
                    blockedKeywords = keywords
                    Log.d(TAG, "Loaded ${keywords.size} blocked keywords")
                    onDomainsUpdated(keywords)
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading keywords", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Database error: ${error.message}")
            }
        }
        valueEventListener?.let { domainsRef.addValueEventListener(it) }
    }

    fun containsBlockedKeyword(domain: String): Boolean {
        val normalizedDomain = domain.lowercase()
        return blockedKeywords.any { keyword ->
            normalizedDomain.contains(keyword)
        }
    }

    fun stopMonitoring() {
        valueEventListener?.let { domainsRef.removeEventListener(it) }
        valueEventListener = null
    }
}