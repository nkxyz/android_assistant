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

    companion object {
        private const val TAG = "xyzMainActivity"
    }

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var shizukuStatusText: TextView  // 新增
    private lateinit var checkShizukuButton: Button  // 新增

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 启动XyzService，这会触发其onCreate方法
        val intent = Intent(this, XyzService::class.java)
        startService(intent)
        checkPermission(0)
        initViews()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()

        // 停止悬浮窗服务
        try {
            stopFloatingService()
        } catch (e: Exception) {
            Log.e(TAG, "停止悬浮窗服务失败", e)
        }
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
        shizukuStatusText = findViewById(R.id.shizukuStatusText)  // 新增
        checkShizukuButton = findViewById(R.id.checkShizukuButton)  // 新增

        enableButton.setOnClickListener {
            openAccessibilitySettings()
        }

        // 新增Shizuku检查按钮点击事件
        checkShizukuButton.setOnClickListener {
            updateStatus()
        }

        startButton.setOnClickListener {
            if (isAnyAccessibilityServiceAvailable()) {
                startFloatingService()
            } else {
                Toast.makeText(this, "请先启用无障碍服务或确保Shizuku服务正常运行", Toast.LENGTH_SHORT).show()
            }
        }

        stopButton.setOnClickListener {
            stopFloatingService()
        }
    }

    private fun updateStatus() {
        val isTraditionalEnabled = isAccessibilityServiceEnabled()
        val isShizukuEnabled = isShizukuAccessibilityServiceAvailable()
        val isAnyEnabled = isTraditionalEnabled || isShizukuEnabled

        Log.d(TAG, "updateStatus: isTraditionalEnabled: $isTraditionalEnabled, isShizukuEnabled: $isShizukuEnabled, isAnyEnabled: $isAnyEnabled")

        // 更新状态文本，显示具体的服务状态
        statusText.text = when {
            isTraditionalEnabled && isShizukuEnabled -> "无障碍服务已启用 (传统服务 + Shizuku系统服务)"
            isTraditionalEnabled -> "无障碍服务已启用 (传统服务)"
            isShizukuEnabled -> "无障碍服务已启用 (Shizuku系统服务)"
            else -> "无障碍服务未启用"
        }

        // 如果Shizuku系统服务可用，则不需要启用传统无障碍服务
        enableButton.isEnabled = !isTraditionalEnabled
        startButton.isEnabled = isAnyEnabled
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)

        for (service in enabledServices) {
            if (service.resolveInfo.serviceInfo.packageName == packageName) {
                Log.d(TAG, "isAccessibilityServiceEnabled: ${service.resolveInfo.serviceInfo.packageName} is enabled")
                return true
            }
        }
        Log.d(TAG, "isAccessibilityServiceEnabled: no enabled service found")
        return false
    }

    /**
     * 检查Shizuku系统级AccessibilityService是否可用
     */
    private fun isShizukuAccessibilityServiceAvailable(): Boolean {
        return try {
            val service = XyzService.getInstance()
            service?.isSystemAccessibilityServiceAvailable() == true
        } catch (e: Exception) {
            Log.e(TAG, "检查Shizuku AccessibilityService失败", e)
            false
        }
    }

    /**
     * 检查是否有可用的无障碍服务（传统服务或Shizuku服务）
     */
    private fun isAnyAccessibilityServiceAvailable(): Boolean {
        return isAccessibilityServiceEnabled() || isShizukuAccessibilityServiceAvailable()
    }

    private fun openAccessibilitySettings() {
        try {
            // 检查Shizuku服务状态
            val isShizukuAvailable = isShizukuAccessibilityServiceAvailable()

            val message = if (isShizukuAvailable) {
                "Shizuku系统服务已可用，应用可以正常使用"
            } else {
                "请确保Shizuku服务正常运行并已授权给本应用"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()

        } catch (e: Exception) {
            Log.e(TAG, "检查Shizuku服务状态失败: ${e.message}")
            Toast.makeText(this, "请确保Shizuku服务正常运行并已授权给本应用", Toast.LENGTH_LONG).show()
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
        Log.d(TAG, "onRequestPermissionsResult: $requestCode, $granted")
    }

    private fun checkPermission(code: Int): Boolean {
        if (Shizuku.isPreV11()) {
            // Pre-v11 is unsupported
            Log.d(TAG, "Shizuku pre-v11 is not supported")
            return false
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            // Granted
            Log.d(TAG, "Shizuku permission granted")
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            // Users choose "Deny and don't ask again"
            Log.d(TAG, "Shizuku permission denied")
            return false
        } else {
            // Request the permission
            Log.d(TAG, "Request Shizuku permission")
            Shizuku.requestPermission(code)
            return false
        }
    }
}
