package com.xyz.xyzassister

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors
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

    private val handler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        Log.i(TAG, "悬浮窗服务创建")
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

        Log.i(TAG, "开始销毁悬浮窗服务...")

        // 停止正在运行的辅助功能
        try {
            if (isAssistantActive) {
                stopAssistant()
                Log.i(TAG, "已停止辅助功能")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止辅助功能失败", e)
        }

        // 关闭后台线程池
        try {
            backgroundExecutor.shutdown()
            // 等待线程池关闭，最多等待5秒
            if (!backgroundExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow()
                Log.w(TAG, "强制关闭后台线程池")
            }
            Log.i(TAG, "后台线程池关闭完成")
        } catch (e: Exception) {
            Log.e(TAG, "关闭后台线程池失败", e)
            backgroundExecutor.shutdownNow()
        }

        // 移除悬浮窗
        try {
            removeFloatingWindow()
            Log.i(TAG, "悬浮窗移除完成")
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败", e)
        }

        // 重置状态变量
        try {
            isAssistantActive = false
            isHidden = false
            Log.i(TAG, "状态变量重置完成")
        } catch (e: Exception) {
            Log.e(TAG, "重置状态变量失败", e)
        }

        Log.i(TAG, "悬浮窗服务销毁完成")
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
            .setSmallIcon(R.mipmap.ic_launcher)
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

            // 为边缘指示器添加点击监听器，点击后恢复悬浮窗
            val edgeIndicator = view.findViewById<View>(R.id.edgeIndicator)
            edgeIndicator.setOnClickListener {
                if (isHidden) {
                    hideToEdge() // 调用hideToEdge()来恢复窗口，因为它已经有切换逻辑
                    Log.d(TAG, "点击边缘指示器，恢复悬浮窗")
                }
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
        Log.d(TAG, "开始启动辅助功能...")

        // 1. 先隐藏悬浮窗到屏幕侧边
        hideToEdgeForAssistant()

        // 2. 等待悬浮窗隐藏完成后检查当前应用
        handler.postDelayed({
            checkCurrentAppAndStartProcess()
        }, 500) // 等待500ms让悬浮窗隐藏动画完成
    }

    /**
     * 为辅助功能隐藏悬浮窗到边缘
     */
    private fun hideToEdgeForAssistant() {
        floatingView?.let { view ->
            val layoutParams = view.layoutParams as WindowManager.LayoutParams

            if (!isHidden) {
                // 隐藏到右边缘
                val displayMetrics = resources.displayMetrics
                layoutParams.x = displayMetrics.widthPixels - 30 // 只露出30像素
                isHidden = true

                // 隐藏按钮，只显示一个小的拖拽区域
                val container = view.findViewById<LinearLayout>(R.id.buttonContainer)
                container.visibility = View.GONE

                // 显示边缘指示器
                val edgeIndicator = view.findViewById<View>(R.id.edgeIndicator)
                edgeIndicator.visibility = View.VISIBLE

                windowManager.updateViewLayout(view, layoutParams)

                Log.d(TAG, "悬浮窗已隐藏到边缘，让出活跃窗口")
                Toast.makeText(this, "悬浮窗已隐藏，开始检测应用...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 检查当前应用并启动相应流程
     */
    private fun checkCurrentAppAndStartProcess() {
        backgroundExecutor.execute {
            try {
                val accessibilityService = XyzAccessibilityService.getInstance()
                if (accessibilityService == null) {
                    handler.post {
                        Toast.makeText(this@FloatingWindowService, "无障碍服务未连接，无法启动辅助功能", Toast.LENGTH_LONG).show()
                        restoreFloatingWindow()
                    }
                    return@execute
                }

                // 检查当前活跃窗口
                val currentActivity = accessibilityService.getCurrentActivity()
                Log.d(TAG, "当前检测到的应用: $currentActivity")

                handler.post {
//                    if (currentActivity?.contains("cn.damai") == true) {
//                        Log.d(TAG, "检测到大麦应用，开始执行抢票流程")
//                        Toast.makeText(this@FloatingWindowService, "检测到大麦应用，开始抢票流程...", Toast.LENGTH_SHORT).show()
//
//
//                    } else {
//                        Log.d(TAG, "当前应用不是大麦应用: $currentActivity")
//                        Toast.makeText(this@FloatingWindowService, "当前应用不是大麦应用，请打开大麦应用后重试", Toast.LENGTH_LONG).show()
//
//                        // 恢复悬浮窗显示
//                        restoreFloatingWindow()
//                    }
                    // 标记辅助功能为激活状态
                    isAssistantActive = true
                    updateButtonStates()

                    // 在后台线程执行抢票流程
                    startTicketGrabbingInBackground()

                }

            } catch (e: Exception) {
                Log.e(TAG, "检查当前应用时发生异常", e)
                handler.post {
                    Toast.makeText(this@FloatingWindowService, "检测应用失败: ${e.message}", Toast.LENGTH_LONG).show()
                    restoreFloatingWindow()
                }
            }
        }
    }

    /**
     * 在后台线程执行抢票流程
     */
    private fun startTicketGrabbingInBackground() {
        backgroundExecutor.execute {
            try {
                val accessibilityService = XyzAccessibilityService.getInstance()
                if (accessibilityService != null) {
                    Log.d(TAG, "开始执行抢票流程...")

                    // 执行抢票流程
                    val success = accessibilityService.startTicketGrabbingProcess()

                    handler.post {
                        // 检查是否是用户主动停止
                        val isStoppedByUser = !accessibilityService.isTicketGrabbingProcessActive()

                        if (isStoppedByUser) {
                            Toast.makeText(this@FloatingWindowService, "抢票流程已被用户停止", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "抢票流程被用户停止")
                        } else if (success) {
                            Toast.makeText(this@FloatingWindowService, "抢票流程执行成功！", Toast.LENGTH_LONG).show()
                            Log.d(TAG, "抢票流程执行成功")
                        } else {
                            Toast.makeText(this@FloatingWindowService, "抢票流程执行完成，但未成功抢到票", Toast.LENGTH_LONG).show()
                            Log.d(TAG, "抢票流程执行完成，但未成功")
                        }

                        // 流程结束后恢复悬浮窗
                        restoreFloatingWindow()
                    }
                } else {
                    handler.post {
                        Toast.makeText(this@FloatingWindowService, "无障碍服务连接丢失", Toast.LENGTH_LONG).show()
                        restoreFloatingWindow()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "执行抢票流程时发生异常", e)
                handler.post {
                    Toast.makeText(this@FloatingWindowService, "抢票流程执行异常: ${e.message}", Toast.LENGTH_LONG).show()
                    restoreFloatingWindow()
                }
            }
        }
    }

    /**
     * 恢复悬浮窗显示
     */
    private fun restoreFloatingWindow() {
        floatingView?.let { view ->
            val layoutParams = view.layoutParams as WindowManager.LayoutParams

            if (isHidden) {
                // 从边缘恢复
                layoutParams.x = 100
                isHidden = false

                // 显示按钮
                val container = view.findViewById<LinearLayout>(R.id.buttonContainer)
                container.visibility = View.VISIBLE

                // 隐藏边缘指示器
                val edgeIndicator = view.findViewById<View>(R.id.edgeIndicator)
                edgeIndicator.visibility = View.GONE

                windowManager.updateViewLayout(view, layoutParams)

                Log.d(TAG, "悬浮窗已恢复显示")
            }
        }

        // 重置辅助功能状态
        isAssistantActive = false
        updateButtonStates()
    }

    private fun stopAssistant() {
        Log.d(TAG, "停止辅助功能")

        // 停止抢票流程
        val accessibilityService = XyzAccessibilityService.getInstance()
        if (accessibilityService != null) {
            accessibilityService.stopTicketGrabbingProcess()
            Log.d(TAG, "已发送停止抢票流程指令")
        }

        // 恢复悬浮窗显示
        restoreFloatingWindow()

        Toast.makeText(this, "辅助功能已停止", Toast.LENGTH_SHORT).show()
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
        val accessibilityService = XyzAccessibilityService.getInstance()
        if (accessibilityService != null) {
            // 打印当前窗口信息
            accessibilityService.printCurrentWindowInfo()

            // 打印屏幕元素
            accessibilityService.printScreenElements()

            // 显示当前Activity ID信息
            val activityId = accessibilityService.getCurrentActivityId()
            Log.d("FloatingWindowService", "当前Activity ID: ${activityId ?: "null"}")

            Toast.makeText(this, "窗口信息和屏幕元素已打印到logcat", Toast.LENGTH_SHORT).show()
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
