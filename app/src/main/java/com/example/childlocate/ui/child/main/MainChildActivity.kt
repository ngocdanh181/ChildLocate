package com.example.childlocate.ui.child.main

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModelProvider
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.example.childlocate.MyFirebaseManager
import com.example.childlocate.R
import com.example.childlocate.data.model.Task
import com.example.childlocate.databinding.ActivityMainChildBinding
import com.example.childlocate.service.AudioStreamingForegroundService
import com.example.childlocate.service.WebFilterAccessibilityService
import com.example.childlocate.service.WebFilterVpnService
//import com.example.childlocate.service.WebFilterVpnService
import com.example.childlocate.ui.child.childchat.ChildChatActivity
import com.example.childlocate.ui.child.main.LocationWorker.Companion.notificationId
import com.example.childlocate.ui.parent.task.TaskAdapter
import com.example.childlocate.utils.AccessibilityUtil
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.concurrent.TimeUnit


class MainChildActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainChildBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: MainChildViewModel by lazy {
        ViewModelProvider(this)[MainChildViewModel::class.java]
    }
    private lateinit var parentId: String
    private lateinit var childId: String
    private var alertDialog1: AlertDialog? = null


    private var isAlertVisible = false

    private lateinit var tasksAdapter: TaskAdapter
    private val ACCESSIBILITY_PERMISSION_REQUEST_CODE = 5555


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainChildBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE)
        childId = sharedPreferences.getString("childId", null).toString()
        parentId = sharedPreferences.getString("parentId", null).toString()

        //parentId = intent.getStringExtra("receiverId") ?: throw IllegalArgumentException("receiverId is missing")
        //childId = intent.getStringExtra("senderId") ?: throw IllegalArgumentException("senderId is missing")
        Log.d("ChildMainActivity","$childId and $parentId")
        //chuyen sang audioStreamingForeground Service
        startPersistentService()


        val PERMISSION_REQUEST_CODE=2000
        requestPermissions(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.PACKAGE_USAGE_STATS
            ),
            PERMISSION_REQUEST_CODE
        )


        MyFirebaseManager.initFirebase(this)
        checkPermissionsAndConditions()

        setupRecyclerView()
        observeTasks()

        // Gọi loadTasksForChild với childId thích hợp
        viewModel.loadTasksForChild(childId)
        Log.d("MainActivity","$childId")

        binding.chatWithParent.setOnClickListener {
            val intent = Intent(this, ChildChatActivity::class.java).apply {
                putExtra("senderId", childId)
                putExtra("receiverId", parentId)
            }
            startActivity(intent)
        }

        binding.btnCall.setOnClickListener {
            val phoneNumber = "0987654321"
            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            startActivity(callIntent)
        }

        binding.shareLocation.setOnClickListener {
            checkPermissionsAndConditions()
            scheduleLocationWorker()
        }

        binding.stopLocation.setOnClickListener {
            stopLocationSharing()
        }

        binding.imageView.setOnClickListener {
            isAlertVisible = !isAlertVisible
            val projectId = "childlocatedemo-6c5da"
            if (isAlertVisible) {
                binding.imageView.setImageResource(R.drawable.baseline_notifications_off_24)
                viewModel.sendWarningToParent(projectId)
            } else {
                binding.imageView.setImageResource((R.drawable.baseline_notifications_24))
                viewModel.stopSendWarningToParent(projectId)
            }
        }

        
        //yeu cau quyen UsageStatsPermission
        requestUsageStatsPermission()
        // Kiểm tra quyền overlay khi khởi động app
        checkOverlayPermission()
        //check quyen accessbility service
        checkAccessibilityService()
        startWebFilterService()
        //startVpnService()
        //startVpnServiceDirectly()
        // Handle warning from accessibility service
        if (intent.getBooleanExtra("show_warning", false)) {
            showBlockedContentWarning()
        }
    }

    /*private fun startVpnService() {
        // Kiểm tra VPN permission
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            // Nếu chưa có permission, yêu cầu user cho phép
            startActivityForResult(vpnIntent, REQUEST_VPN_PERMISSION)
        } else {
            // Đã có permission, start service luôn
            startVpnServiceDirectly()
        }
    }*/

    private fun startVpnServiceDirectly() {
        val serviceIntent = Intent(this, WebFilterVpnService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }


    private fun checkAccessibilityService() {
        if (!AccessibilityUtil.isAccessibilityServiceEnabled(this)) {
            alertDialog1=AlertDialog.Builder(this)
                .setTitle("Yêu cầu quyền")
                .setMessage("Ứng dụng cần quyền truy cập đặc biệt để giới hạn thời gian sử dụng ứng dụng. Vui lòng bật dịch vụ trong cài đặt.")
                .setPositiveButton("Đi đến Cài đặt") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivityForResult(intent, ACCESSIBILITY_PERMISSION_REQUEST_CODE)
                }
                .setNegativeButton("Để sau", null)
                .setCancelable(false)
                .show()
        }else{
            // Start service if permission is already granted
            startWebFilterService()
        }
    }

    private fun startWebFilterService() {
        val intent = Intent(this, WebFilterAccessibilityService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
            Log.d("MainChildAcitivity","Chuyển sang intent web filter oke")
        } else {
            startService(intent)
        }
    }

    private fun showBlockedContentWarning() {
        AlertDialog.Builder(this)
            .setTitle("Cảnh báo")
            .setMessage("Nội dung bị chặn do vi phạm quy định")
            .setPositiveButton("Đã hiểu", null)
            .show()
    }
    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            // Hiển thị dialog giải thích về quyền
            AlertDialog.Builder(this)
                .setTitle("Cần cấp quyền")
                .setMessage("Ứng dụng cần quyền hiển thị trên ứng dụng khác để có thể giới hạn thời gian sử dụng ứng dụng con")
                .setPositiveButton("Cấp quyền") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Để sau", null)
                .show()
        }
    }


    private fun requestUsageStatsPermission() {
        if (!hasUsageStatsPermission(this)) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }



    private fun observeTasks() {
        viewModel.tasks.observe(this) { tasks ->
            tasksAdapter.submitList(tasks)
        }

        tasksAdapter.setOnTaskStatusChangeListener(object : TaskAdapter.OnTaskStatusChangeListener {
            override fun onTaskStatusChanged(task: Task, isCompleted: Boolean) {
                viewModel.updateTaskStatus(childId, task.id, isCompleted)
            }
        })
    }

    private fun setupRecyclerView() {
        tasksAdapter = TaskAdapter()
        binding.taskRecyclerView.adapter = tasksAdapter
    }

    private fun checkPermissionsAndConditions() {
        if (!isInternetAvailable()) {
            Toast.makeText(this, "Không có kết nối Internet", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isLocationEnabled()) {
            Toast.makeText(this, "Dịch vụ định vị chưa bật", Toast.LENGTH_SHORT).show()
            return
        }

        requestLocationPermissions()


    }

    private fun startPersistentService() {
        Intent(this, AudioStreamingForegroundService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun stopLocationSharing() {
        WorkManager.getInstance(this).cancelAllWorkByTag(LocationWorker.TAG)
        Log.d("Location", "Location share stopped")
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancel(notificationId)
    }

    private fun scheduleLocationWorker() {
        val locationRequest = PeriodicWorkRequest.Builder(LocationWorker::class.java,
            15, TimeUnit.MINUTES)
            .addTag(LocationWorker.TAG)
            .build()
        WorkManager.getInstance(this).enqueue(locationRequest)
        Log.d("SecondActivity", "Location sharing scheduled in 15 minutes")
    }

    private fun requestLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )

            ActivityCompat.requestPermissions(this, permissions, LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    // Handle permission result
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ACCESSIBILITY_PERMISSION_REQUEST_CODE) {
            if (AccessibilityUtil.isAccessibilityServiceEnabled(this)) {
                startWebFilterService()
            }
        }
        if (requestCode == REQUEST_VPN_PERMISSION && resultCode == RESULT_OK) {
            startVpnServiceDirectly()
        }
    }
    override fun onStart() {
        super.onStart()
        checkPermissionsAndConditions()
    }

    override fun onDestroy() {
        super.onDestroy()
        alertDialog1?.dismiss()
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 100
        private const val REQUEST_VPN_PERMISSION = 97
    }
}
