package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderDeadListener
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Shizuku.addBinderReceivedListenerSticky(BINDER_RECEIVED_LISTENER);
        Shizuku.addBinderDeadListener(BINDER_DEAD_LISTENER);
        Shizuku.addRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
        checkPermission(123123)
        initViews()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(REQUEST_PERMISSION_RESULT_LISTENER)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun initViews() {
        statusText = findViewById(R.id.statusText)
        enableButton = findViewById(R.id.enableButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        enableButton.setOnClickListener {
            openAccessibilitySettings()
        }

        startButton.setOnClickListener {
            if (isAccessibilityServiceEnabled()) {
                startFloatingService()
            } else {
                Toast.makeText(this, "请先启用无障碍服务", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            stopFloatingService()
        }
    }

    private fun updateStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        statusText.text = if (isEnabled) "无障碍服务已启用" else "无障碍服务未启用"
        enableButton.isEnabled = !isEnabled
        startButton.isEnabled = isEnabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                return true
            }
        }
        return false
    }

    private fun openAccessibilitySettings() {
        try {
            // 方法1：直接跳转到本应用的无障碍服务设置页面
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

            // 添加额外参数，尝试直接定位到本应用的服务
            intent.putExtra(":settings:fragment_args_key", "${packageName}/${XyzAccessibilityService::class.java.name}")
            intent.putExtra(":settings:show_fragment_args", Bundle().apply {
                putString("package", packageName)
            })

            startActivity(intent)

            // 显示更详细的指导信息
            Toast.makeText(this, "请在无障碍设置中找到并启用 'XYZ无障碍助手' 服务", Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            // 如果上述方法失败，回退到通用的无障碍设置页面
            Log.e("MainActivity", "无法直接跳转到应用设置页面: ${e.message}")
            val fallbackIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            fallbackIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallbackIntent)
            Toast.makeText(this, "请在设置中找到并启用本应用的无障碍服务", Toast.LENGTH_LONG).show()
        }
    }

    private fun startFloatingService() {
        if (checkOverlayPermission()) {
            val intent = Intent(this, FloatingWindowService::class.java)
            startForegroundService(intent)
            Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show()
        } else {
            requestOverlayPermission()
        }
    }

    private fun checkOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        stopService(intent)
        Toast.makeText(this, "悬浮窗服务已停止", Toast.LENGTH_SHORT).show()
    }


    private fun onRequestPermissionsResult(requestCode: Int, grantResult: Int) {
        val granted = grantResult == PackageManager.PERMISSION_GRANTED
        // Do stuff based on the result and the request code
        Log.d("xyzAssisterMain", "onRequestPermissionsResult: $requestCode, $granted")
    }

    private val REQUEST_PERMISSION_RESULT_LISTENER =
        Shizuku.OnRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
            this.onRequestPermissionsResult(
                requestCode,
                grantResult
            )
        }

    private fun checkPermission(code: Int): Boolean {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            return false
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            return false
        } else {
            // Request the permission
            Shizuku.requestPermission(code)
            return false
        }
    }

    private val BINDER_RECEIVED_LISTENER = OnBinderReceivedListener {
        if (Shizuku.isPreV11()) {
            Log.d("xyzAssisterMain", "Shizuku pre-v11 is not supported")
        } else {
            Log.d("xyzAssisterMain", "Binder received")
        }
    }

    private val BINDER_DEAD_LISTENER = OnBinderDeadListener {
        Log.d("xyzAssisterMain", "Binder dead")
    }
}
