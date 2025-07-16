package com.xyz.xyzassister

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import org.json.JSONObject
import kotlin.random.Random

/**
 * 协调服务
 * 统一管理传统无障碍服务和Shizuku系统服务
 */
class XyzService : Service() {

    companion object {
        private const val TAG = "XyzService"
        private var instance: XyzService? = null

        /**
         * 获取服务实例
         */
        fun getInstance(): XyzService? = instance
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val activeIndicators = mutableListOf<View>()

    // 存储当前窗口/Activity信息
    private var currentPackageName: String? = null
    private var currentClassName: String? = null
    private var currentActivityName: String? = null

    // 系统无障碍服务事件回调
    private var systemAccessibilityCallback: IAccessibilityEventCallback? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        Log.i(TAG, "XyzService已创建")

        // 初始化 Shizuku 服务管理器
        ShizukuServiceManager.getInstance(this)?.initializeShizuku()

        // 注册系统无障碍服务事件监听
        registerSystemAccessibilityEventListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "XyzService已启动")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "XyzService开始销毁")

        // 取消注册事件监听
        unregisterSystemAccessibilityEventListener()

        // 停止所有正在运行的流程
        try {
            if (TicketGrabbingManager.getInstance().isTicketGrabbingProcessActive()) {
                stopTicketGrabbingProcess()
                Log.i(TAG, "已停止抢票流程")
            }
        } catch (e: Exception) {
            Log.e(TAG, "停止抢票流程失败", e)
        }

        // 清理点击指示器
        try {
            clearAllClickIndicators()
            Log.i(TAG, "点击指示器清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理点击指示器失败", e)
        }

        // 清理 Shizuku 资源
        try {
            ShizukuServiceManager.getInstance()?.cleanupShizuku()
            Log.i(TAG, "Shizuku 资源清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理 Shizuku 资源失败", e)
        }

        // 重置状态变量
        try {
            currentPackageName = null
            currentClassName = null
            currentActivityName = null
            Log.i(TAG, "状态变量重置完成")
        } catch (e: Exception) {
            Log.e(TAG, "重置状态变量失败", e)
        }

        instance = null
        Log.d(TAG, "XyzService销毁完成")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    /**
     * 注册系统无障碍服务事件监听
     */
    private fun registerSystemAccessibilityEventListener() {
        try {
            systemAccessibilityCallback = object : IAccessibilityEventCallback.Stub() {
                override fun onAccessibilityEvent(
                    eventType: Int,
                    packageName: String?,
                    className: String?,
                    text: String?,
                    contentDescription: String?,
                    eventTime: Long,
                    windowId: Int
                ) {
                    handler.post {
                        handleSystemAccessibilityEvent(
                            eventType, packageName, className,
                            text, contentDescription, eventTime, windowId
                        )
                    }
                }

                override fun onInterrupt() {
                    Log.w(TAG, "系统无障碍服务被中断")
                }

                override fun onServiceConnectionChanged(connected: Boolean) {
                    Log.i(TAG, "系统无障碍服务连接状态变化: $connected")
                }
            }

            // 注册到系统无障碍服务
            val shizukuManager = ShizukuServiceManager.getInstance()
            val systemService = shizukuManager?.getSystemAccessibilityService()
            if (systemService != null) {
                val success = systemService.registerEventCallback(
                    systemAccessibilityCallback,
                    AccessibilityEvent.TYPES_ALL_MASK
                )
                if (success) {
                    Log.i(TAG, "成功注册系统无障碍服务事件监听")

                    // 设置事件过滤器，只监听窗口状态变化
                    systemService.setEventFilter(
                        null,  // 监听所有包名
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                    )
                } else {
                    Log.w(TAG, "注册系统无障碍服务事件监听失败")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册系统无障碍服务事件监听异常", e)
        }
    }

    /**
     * 取消注册系统无障碍服务事件监听
     */
    private fun unregisterSystemAccessibilityEventListener() {
        try {
            systemAccessibilityCallback?.let { callback ->
                val shizukuManager = ShizukuServiceManager.getInstance()
                val systemService = shizukuManager?.getSystemAccessibilityService()
                systemService?.unregisterEventCallback(callback)
                Log.i(TAG, "已取消注册系统无障碍服务事件监听")
            }
        } catch (e: Exception) {
            Log.e(TAG, "取消注册系统无障碍服务事件监听失败", e)
        }
        systemAccessibilityCallback = null
    }

    /**
     * 处理系统无障碍服务事件
     */
    private fun handleSystemAccessibilityEvent(
        eventType: Int,
        packageName: String?,
        className: String?,
        text: String?,
        contentDescription: String?,
        eventTime: Long,
        windowId: Int
    ) {
        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 更新当前窗口信息
                currentPackageName = packageName
                currentClassName = className

                // 从className中提取Activity名称
                if (className?.contains(".") == true) {
                    currentActivityName = className
                }

                Log.d(TAG, "窗口状态改变 [Shizuku]:")
                Log.d(TAG, "  包名: $packageName")
                Log.d(TAG, "  类名: $className")
                Log.d(TAG, "  文本: $text")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 窗口内容改变
                if (packageName != currentPackageName) {
                    currentPackageName = packageName
                    Log.d(TAG, "窗口内容改变，包名更新 [Shizuku]: $packageName")
                }
            }
        }
    }

    /**
     * 强制清理 Shizuku 资源
     */
    fun forceCleanupShizuku() {
        try {
            Log.i(TAG, "开始强制清理Shizuku资源...")
            unregisterSystemAccessibilityEventListener()
            ShizukuServiceManager.getInstance()?.forceCleanupShizuku()
            Log.i(TAG, "强制清理Shizuku资源完成")
        } catch (e: Exception) {
            Log.e(TAG, "强制清理Shizuku资源失败", e)
        }
    }

    /**
     * 点击指定坐标
     */
    fun clickAt(x: Float, y: Float): Boolean {
        // 显示点击指示器
        showClickIndicator(x, y)

        // 优先使用 Binder 服务
        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true &&
            ShizukuServiceManager.getInstance()?.getInstrumentationService() != null) {
            val result = clickAtWithBinderService(x, y)
            if (result) {
                return true
            }
            Log.w(TAG, "Binder服务点击失败，尝试其他方法")
        }

        // 回退到传统无障碍服务手势
        val xyzAccessibilityService = XyzAccessibilityService.getInstance()
        if (xyzAccessibilityService != null) {
            return xyzAccessibilityService.performGestureClick(x, y)
        }

        Log.e(TAG, "所有点击方法都失败了")
        return false
    }

    /**
     * 使用Binder服务点击
     */
    private fun clickAtWithBinderService(x: Float, y: Float): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.click(x, y)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务点击失败", e)
            false
        }
    }

    /**
     * 拖拽操作
     */
    fun dragFromTo(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500): Boolean {
        // 优先使用 Binder 服务
        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true &&
            ShizukuServiceManager.getInstance()?.getInstrumentationService() != null) {
            val result = dragFromToWithBinderService(startX, startY, endX, endY, duration)
            if (result) {
                return true
            }
            Log.w(TAG, "Binder服务拖拽失败，尝试其他方法")
        }

        // 回退到传统无障碍服务手势
        val xyzAccessibilityService = XyzAccessibilityService.getInstance()
        if (xyzAccessibilityService != null) {
            return xyzAccessibilityService.performGestureDrag(startX, startY, endX, endY, duration)
        }

        Log.e(TAG, "所有拖拽方法都失败了")
        return false
    }

    /**
     * 使用Binder服务拖拽
     */
    private fun dragFromToWithBinderService(
        startX: Float, startY: Float, endX: Float, endY: Float,
        duration: Long = 500
    ): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.drag(startX, startY, endX, endY, duration)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务拖拽失败", e)
            false
        }
    }

    /**
     * 长按操作
     */
    fun longClickAt(x: Float, y: Float, duration: Long = 1000): Boolean {
        showClickIndicator(x, y)

        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true &&
            ShizukuServiceManager.getInstance()?.getInstrumentationService() != null) {
            val result = longClickAtWithBinderService(x, y, duration)
            if (result) {
                return true
            }
        }

        // 回退到传统无障碍服务
        val xyzAccessibilityService = XyzAccessibilityService.getInstance()
        if (xyzAccessibilityService != null) {
            return xyzAccessibilityService.performGestureLongClick(x, y, duration)
        }

        return false
    }

    /**
     * 使用Binder服务长按
     */
    private fun longClickAtWithBinderService(x: Float, y: Float, duration: Long = 1000): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.longClick(x, y, duration)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务长按失败", e)
            false
        }
    }

    /**
     * 双击操作
     */
    fun doubleClickAt(x: Float, y: Float): Boolean {
        showClickIndicator(x, y)

        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true &&
            ShizukuServiceManager.getInstance()?.getInstrumentationService() != null) {
            val result = doubleClickAtWithBinderService(x, y)
            if (result) {
                return true
            }
        }

        // 回退：两次点击
        return clickAt(x, y) && clickAt(x, y)
    }

    /**
     * 使用Binder服务双击
     */
    private fun doubleClickAtWithBinderService(x: Float, y: Float): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.doubleClick(x, y)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务双击失败", e)
            false
        }
    }

    /**
     * 输入文本
     */
    fun inputText(text: String): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.inputText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务文本输入失败", e)
            false
        }
    }

    /**
     * 发送按键事件
     */
    fun sendKeyEvent(keyCode: Int): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.sendKeyEvent(keyCode)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务按键事件失败", e)
            false
        }
    }

    /**
     * 显示点击指示器
     */
    private fun showClickIndicator(x: Float, y: Float) {
        handler.post {
            try {
                val indicator = ImageView(this)
                indicator.setImageResource(R.drawable.click_indicator)

                val params = WindowManager.LayoutParams(
                    dpToPx(24),
                    dpToPx(24),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )

                params.gravity = Gravity.TOP or Gravity.START
                params.x = (x - dpToPx(12)).toInt()
                params.y = (y - dpToPx(12)).toInt()

                windowManager.addView(indicator, params)
                activeIndicators.add(indicator)

                // 2秒后自动移除指示器
                handler.postDelayed({
                    removeClickIndicator(indicator)
                }, 2000)

            } catch (e: Exception) {
                Log.e(TAG, "显示点击指示器失败", e)
            }
        }
    }

    /**
     * 移除点击指示器
     */
    private fun removeClickIndicator(indicator: View) {
        handler.post {
            try {
                if (activeIndicators.contains(indicator)) {
                    windowManager.removeView(indicator)
                    activeIndicators.remove(indicator)
                }
            } catch (e: Exception) {
                Log.e(TAG, "移除点击指示器失败", e)
            }
        }
    }

    /**
     * 清理所有点击指示器
     */
    private fun clearAllClickIndicators() {
        handler.post {
            try {
                activeIndicators.forEach { indicator ->
                    try {
                        windowManager.removeView(indicator)
                    } catch (e: Exception) {
                        Log.w(TAG, "移除单个指示器失败", e)
                    }
                }
                activeIndicators.clear()
            } catch (e: Exception) {
                Log.e(TAG, "清理所有点击指示器失败", e)
            }
        }
    }

    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    /**
     * 检查服务是否连接
     */
    fun isServiceConnected(): Boolean {
        return instance != null
    }

    /**
     * 获取当前Activity
     */
    fun getCurrentActivity(): String? {
        // 优先返回从系统服务获取的信息
        val systemActivity = currentActivityName ?: currentClassName
        if (!systemActivity.isNullOrEmpty()) {
            return systemActivity
        }

        // 从传统无障碍服务获取
        val xyzAccessibilityService = XyzAccessibilityService.getInstance()
        return xyzAccessibilityService?.getCurrentActivity()
    }

    /**
     * 获取当前包名
     */
    fun getCurrentPackageName(): String? {
        // 优先返回从系统服务获取的信息
        if (!currentPackageName.isNullOrEmpty()) {
            return currentPackageName
        }

        // 从传统无障碍服务获取
        val xyzAccessibilityService = XyzAccessibilityService.getInstance()
        return xyzAccessibilityService?.rootInActiveWindow?.packageName?.toString()
    }

    /**
     * 获取当前窗口信息
     */
    fun getCurrentWindowInfo(): String {
        return buildString {
            append("========== 当前窗口信息 ==========\n")

            // 显示当前维护的信息
            append("包名: ${currentPackageName ?: "未知"}\n")
            append("类名: ${currentClassName ?: "未知"}\n")
            append("Activity: ${currentActivityName ?: "未知"}\n")

            // 获取系统服务信息
            try {
                val shizukuManager = ShizukuServiceManager.getInstance()
                val systemService = shizukuManager?.getSystemAccessibilityService()
                if (systemService != null && systemService.isServiceAvailable()) {
                    append("\n[Shizuku系统服务信息]\n")
                    val windowInfo = systemService.getCurrentWindowInfo()
                    append(windowInfo)
                }
            } catch (e: Exception) {
                append("\n[Shizuku系统服务]: 不可用\n")
            }

            // 获取传统服务信息
            val xyzAccessibilityService = XyzAccessibilityService.getInstance()
            if (xyzAccessibilityService != null) {
                append("\n[传统无障碍服务信息]\n")
                append(xyzAccessibilityService.getCurrentWindowInfo())
            } else {
                append("\n[传统无障碍服务]: 未连接\n")
            }

            append("\n=====================================")
        }
    }

    /**
     * 打印当前窗口信息
     */
    fun printCurrentWindowInfo() {
        Log.d(TAG, getCurrentWindowInfo())
    }

    /**
     * 检查系统级无障碍服务是否可用
     */
    fun isSystemAccessibilityServiceAvailable(): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService()
            service?.isServiceAvailable() == true
        } catch (e: Exception) {
            Log.e(TAG, "检查系统级无障碍服务失败", e)
            false
        }
    }

    // 抢票相关方法
    /**
     * 开始抢票流程
     */
    fun startTicketGrabbingProcess(): Boolean {
        return TicketGrabbingManager.getInstance().startTicketGrabbingProcess()
    }

    /**
     * 停止抢票流程
     */
    fun stopTicketGrabbingProcess() {
        TicketGrabbingManager.getInstance().stopTicketGrabbingProcess()
    }

    /**
     * 检查抢票流程是否正在运行
     */
    fun isTicketGrabbingProcessActive(): Boolean {
        return TicketGrabbingManager.getInstance().isTicketGrabbingProcessActive()
    }
}