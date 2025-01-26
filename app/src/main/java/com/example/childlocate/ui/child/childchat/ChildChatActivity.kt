package com.example.childlocate.ui.child.childchat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.childlocate.R

class ChildChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_chat)

        // Nhận dữ liệu từ Intent
        val parentId = intent.getStringExtra("receiverId")
        val childId = intent.getStringExtra("senderId")

        // Tạo mới Fragment và truyền dữ liệu vào
        if (savedInstanceState == null) {
            val fragment = ChildChatDetailFragment.newInstance(parentId, childId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commitNow()
        }


    }
}