package com.example.childlocate.ui.parent.login

// LoginActivity.kt

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.childlocate.databinding.ActivityLoginBinding
import com.example.childlocate.ui.ForgotPasswordActivity
import com.example.childlocate.ui.parent.MainActivity


class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel: AuthViewModel by lazy {
        ViewModelProvider(this)[AuthViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loginButton.setOnClickListener {
            if (isInternetAvailable()) {
                val email = binding.emailEditText.text.toString()
                val password = binding.passwordEditText.text.toString()
                viewModel.loginUser(email, password)
            } else {
                Toast.makeText(this, "Không có kết nối Internet", Toast.LENGTH_SHORT).show()
            }
        }


        viewModel.authState.observe(this, Observer { state ->
            when (state) {
                is AuthState.LoggedIn -> {
                    Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is AuthState.Error -> {
                    Toast.makeText(this, "Login Failed: ${state.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        })

        binding.signUpText.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
        binding.forgotPasswordText.setOnClickListener{
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
