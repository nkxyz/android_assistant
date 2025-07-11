package com.xyz.xyzassister

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class FloatingWindowService : Service() {
    
    companion object {
        private const val TAG = "FloatingWindowService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_window_channel"
    }
    
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var isAssistantActive = false
    private var isHidden = false
    
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var hideButton: Button
    private lateinit var printButton: Button
    
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.d(TAG, "悬浮窗服务创建")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        
        if (floatingView == null) {
            createFloatingWindow()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        removeFloatingWindow()
        Log.d(TAG, "悬浮窗服务销毁")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "XYZ无障碍助手悬浮窗服务"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("XYZ无障碍助手")
            .setContentText("悬浮窗服务运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }
    
    private fun createFloatingWindow() {
        try {
            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.floating_window, null)
            
            initFloatingViewComponents()
            
            val layoutParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }
            
            windowManager.addView(floatingView, layoutParams)
            setupDragListener(layoutParams)
            
            Log.d(TAG, "悬浮窗创建成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败: ${e.message}")
            Toast.makeText(this, "创建悬浮窗失败，请检查悬浮窗权限", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun initFloatingViewComponents() {
        floatingView?.let { view ->
            startButton = view.findViewById(R.id.startButton)
            stopButton = view.findViewById(R.id.stopButton)
            hideButton = view.findViewById(R.id.hideButton)
            printButton = view.findViewById(R.id.printButton)
            
            startButton.setOnClickListener {
                startAssistant()
            }
            
            stopButton.setOnClickListener {
                stopAssistant()
            }
            
            hideButton.setOnClickListener {
                hideToEdge()
            }
            
            printButton.setOnClickListener {
                printScreenElements()
            }
            
            updateButtonStates()
        }
    }
    
    private fun setupDragListener(layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }
    
    private fun startAssistant() {
        isAssistantActive = true
        updateButtonStates()
        Toast.makeText(this, "辅助功能已启动", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "辅助功能启动")
    }
    
    private fun stopAssistant() {
        isAssistantActive = false
        updateButtonStates()
        Toast.makeText(this, "辅助功能已停止", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "辅助功能停止")
    }
    
    private fun hideToEdge() {
        floatingView?.let { view ->
            val layoutParams = view.layoutParams as WindowManager.LayoutParams
            
            if (!isHidden) {
                // 隐藏到右边缘
                val displayMetrics = resources.displayMetrics
                layoutParams.x = displayMetrics.widthPixels - 50 // 只露出50像素
                isHidden = true
                
                // 隐藏按钮，只显示一个小的拖拽区域
                val container = view.findViewById<LinearLayout>(R.id.buttonContainer)
                container.visibility = View.GONE
                
                // 显示边缘指示器
                val edgeIndicator = view.findViewById<View>(R.id.edgeIndicator)
                edgeIndicator.visibility = View.VISIBLE
                
                Toast.makeText(this, "悬浮窗已隐藏到边缘", Toast.LENGTH_SHORT).show()
            } else {
                // 从边缘恢复
                layoutParams.x = 100
                isHidden = false
                
                // 显示按钮
                val container = view.findViewById<LinearLayout>(R.id.buttonContainer)
                container.visibility = View.VISIBLE
                
                // 隐藏边缘指示器
                val edgeIndicator = view.findViewById<View>(R.id.edgeIndicator)
                edgeIndicator.visibility = View.GONE
                
                Toast.makeText(this, "悬浮窗已恢复", Toast.LENGTH_SHORT).show()
            }
            
            windowManager.updateViewLayout(view, layoutParams)
        }
    }
    
    private fun printScreenElements() {
        val accessibilityService = XyzAccessibilityService.instance
        if (accessibilityService != null) {
            accessibilityService.printScreenElements()
            Toast.makeText(this, "屏幕元素已打印到logcat", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "无障碍服务未连接", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateButtonStates() {
        startButton.isEnabled = !isAssistantActive
        stopButton.isEnabled = isAssistantActive
    }
    
    private fun removeFloatingWindow() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
                floatingView = null
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮窗失败: ${e.message}")
            }
        }
    }
}