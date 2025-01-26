package com.example.childlocate.ui.child.childstart

// ChildIdActivity.kt
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityChildIdBinding
import com.example.childlocate.ui.child.main.MainChildActivity

class ChildIdActivity : AppCompatActivity() {

    private lateinit var parentId: String

    private lateinit var binding: ActivityChildIdBinding
    private val viewModel: ChildViewModel by lazy {
        ViewModelProvider(this)[ChildViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChildIdBinding.inflate(layoutInflater)
        setContentView(binding.root)




        // Kiểm tra nếu đã lưu trữ userId và childName trong SharedPreferences
        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        val savedChildId = sharedPreferences.getString("childId", null)
        val savedParentId = sharedPreferences.getString("parentId", null)

        Log.d("ChildActivity","$savedChildId")

        if (savedChildId != null && savedParentId != null) {
            // Nếu đã lưu trữ, chuyển người dùng đến MainChildActivity
            val intent = Intent(this, MainChildActivity::class.java).apply {
                putExtra("senderId", savedChildId)
                putExtra("receiverId", savedParentId)
            }
            startActivity(intent)
            finish()
            return
        }

        binding.submitButton.setOnClickListener {
            parentId = binding.childIdEditText.text.toString()
            val childName = binding.childNameEditText.text.toString()
            viewModel.verifyAndSaveChild(parentId, childName)
        }

        viewModel.verificationStatus.observe(this, Observer { isSuccess ->
            if (isSuccess) {
                Toast.makeText(this, "Child linked successfully!", Toast.LENGTH_SHORT).show()
                viewModel.childId.observe(this, Observer { childId ->
                    // Lưu trữ userId và childName vào SharedPreferences


                    sharedPreferences.edit().apply {
                        putString("childId", childId)
                        putString("parentId", parentId)
                        apply()
                    }

                    val intent = Intent(this, MainChildActivity::class.java).apply {
                        putExtra("senderId", childId) // Truyền child ID như một extra
                        putExtra("receiverId", parentId)
                    }
                    startActivity(intent)
                    finish()
                })
            } else {
                Toast.makeText(this, "Invalid Parent ID!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}


