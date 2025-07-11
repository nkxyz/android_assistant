package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var enableButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        updateStatus()
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
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
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
}
