package com.example.childlocate.ui.parent.login


import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivitySignupBinding
import com.example.childlocate.ui.parent.MainActivity

class SignupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private val viewModel: AuthViewModel by lazy {
        ViewModelProvider(this)[AuthViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signUpButton.setOnClickListener {
            val email = binding.emailEditText.text.toString()
            val password = binding.passwordEditText.text.toString()
            viewModel.registerUser(email, password)
        }

        viewModel.authState.observe(this, Observer { state ->
            when(state) {
                is AuthState.LoggedIn -> {
                    Toast.makeText(
                        this,
                        "Registration Successful. Your ID is: ${state.user.userId}",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }

                is AuthState.Error -> {
                    Toast.makeText(
                        this,
                        "Registration Failed: ${state.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                else -> {}
            }
        })
    }
}
