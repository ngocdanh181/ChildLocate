package com.example.childlocate.ui.parent.userinfo


import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.example.childlocate.R
import com.example.childlocate.databinding.FragmentUserInfoBinding
import com.example.childlocate.ui.ChooseUserTypeActivity


class UserInfoFragment : Fragment() {

    private lateinit var binding: FragmentUserInfoBinding
    private val viewModel: UserInfoViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true) {
            // Permissions granted, open gallery
            openGallery()
        } else {
            // Permissions denied
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            viewModel.uploadAvatar(it)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.userId.observe(viewLifecycleOwner, Observer { userId ->
            binding.userIdTextView.text = getString(R.string.userid_format, userId)
        })

        viewModel.childName.observe(viewLifecycleOwner, Observer { childName ->
            binding.childNameTextView.text = getString(R.string.child_name_format, childName)

        })

        viewModel.email.observe(viewLifecycleOwner,Observer{email->
            binding.emailTextView.text = getString(R.string.email_format,email)

        })

        viewModel.passwordChangeResult.observe(viewLifecycleOwner, Observer { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            Log.d("UserInfoFragment", "Password change result: $message")
        })

        viewModel.logoutStatus.observe(viewLifecycleOwner, Observer { isLoggedOut ->
            if (isLoggedOut) {
                // Handle logout
                val intent = Intent(activity, ChooseUserTypeActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)

            }
        })



        viewModel.avatarUrl.observe(viewLifecycleOwner, Observer { avatarUrl ->
            if (avatarUrl.isNotEmpty()) {
                Glide.with(this).load(avatarUrl).into(binding.imageAvatar)
            }else {
                // Display placeholder avatar and text
                binding.imageAvatar.setImageResource(R.drawable.baseline_person_2_24)
                binding.clickToChooseAvatarTextView.visibility = View.VISIBLE
            }
        })

        binding.imageAvatar.setOnClickListener {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
        }


        binding.changePasswordTextView.setOnClickListener {
            showChangePasswordDialog()
        }


        binding.logoutTextView.setOnClickListener {
            viewModel.onLogoutClick()
        }

        viewModel.fetchUserData()
    }

    private fun openGallery() {
        getContent.launch("image/*")
    }

    //dialog change mật khẩu
    private fun showChangePasswordDialog(){
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_change_password, null)
        val currentPasswordEditText = dialogView.findViewById<EditText>(R.id.current_password_edit_text)
        val newPasswordEditText = dialogView.findViewById<EditText>(R.id.new_password_edit_text)

        AlertDialog.Builder(requireContext())
            .setTitle("Change Password")
            .setView(dialogView)
            .setPositiveButton("Confirm") { dialog, _ ->
                val currentPassword = currentPasswordEditText.text.toString()
                val newPassword = newPasswordEditText.text.toString()
                Log.d("UserInfoFragment", "Current password: $currentPassword, New password: $newPassword")
                viewModel.changePassword(currentPassword, newPassword)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}

