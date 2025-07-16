package com.xyz.xyzassister

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import kotlin.random.Random


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

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        Log.i(TAG, "XyzService已创建")

        // 初始化 Shizuku 服务管理器
        ShizukuServiceManager.getInstance(this)?.initializeShizuku()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "XyzService已启动")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "XyzService开始销毁")

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
     * 强制清理 Shizuku 资源 - 可从外部调用
     * 用于应用程序终止时的资源清理
     */
    fun forceCleanupShizuku() {
        try {
            Log.i(TAG, "开始强制清理Shizuku资源...")
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

        // 回退到 Shizuku Shell 命令
        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true) {
            val result = clickAtWithShizukuShell(x, y)
            if (result) {
                return true
            }
            Log.w(TAG, "Shizuku Shell点击失败")
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
     * 使用Shizuku Shell命令点击
     */
    private fun clickAtWithShizukuShell(x: Float, y: Float): Boolean {
        return try {
            // 使用 InstrumentationService 的 click 方法作为替代
            // 因为 Shizuku.newProcess 是私有的，我们通过 InstrumentationService 来实现
            Log.w(TAG, "Shizuku Shell点击暂不可用，使用其他方法")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku Shell点击异常", e)
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
        // 优先使用 Binder 服务
        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true &&
            ShizukuServiceManager.getInstance()?.getInstrumentationService() != null) {
            val result = longClickAtWithBinderService(x, y, duration)
            if (result) {
                return true
            }
            Log.w(TAG, "Binder服务长按失败")
        }

        Log.e(TAG, "所有长按方法都失败了")
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
        // 优先使用 Binder 服务
        if (ShizukuServiceManager.getInstance()?.isShizukuAvailable() == true &&
            ShizukuServiceManager.getInstance()?.isShizukuPermissionGranted() == true &&
            ShizukuServiceManager.getInstance()?.getInstrumentationService() != null) {
            val result = doubleClickAtWithBinderService(x, y)
            if (result) {
                return true
            }
            Log.w(TAG, "Binder服务双击失败")
        }

        Log.e(TAG, "所有双击方法都失败了")
        return false
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
     * 滑块操作
     */
    fun slideSeekBar(startX: Float, startY: Float, endX: Float, endY: Float, steps: Int = 20): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getInstrumentationService() ?: return false
            service.slideSeekBar(startX, startY, endX, endY, steps)
        } catch (e: Exception) {
            Log.e(TAG, "Binder服务滑块操作失败", e)
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

    // 系统级无障碍服务方法
    /**
     * 获取系统级根节点信息
     */
    fun getSystemRootNodeInfo(): String? {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return null
            service.getRootNodeInfo()
        } catch (e: Exception) {
            Log.e(TAG, "获取系统级根节点信息失败", e)
            null
        }
    }

    /**
     * 系统级根据ID查找节点
     */
    fun findSystemNodeById(id: String): String? {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return null
            service.findNodeById(id)
        } catch (e: Exception) {
            Log.e(TAG, "系统级根据ID查找节点失败", e)
            null
        }
    }

    /**
     * 系统级根据文本查找节点
     */
    fun findSystemNodeByText(text: String): String? {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return null
            service.findNodeByText(text)
        } catch (e: Exception) {
            Log.e(TAG, "系统级根据文本查找节点失败", e)
            null
        }
    }

    /**
     * 系统级根据类名查找节点
     */
    fun findSystemNodeByClass(className: String): String? {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return null
            service.findNodeByClass(className)
        } catch (e: Exception) {
            Log.e(TAG, "系统级根据类名查找节点失败", e)
            null
        }
    }

    /**
     * 系统级点击节点
     */
    fun clickSystemNodeById(id: String): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return false
            service.clickNodeById(id)
        } catch (e: Exception) {
            Log.e(TAG, "系统级点击节点失败", e)
            false
        }
    }

    /**
     * 系统级点击节点
     */
    fun clickSystemNodeByText(text: String): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return false
            service.clickNodeByText(text)
        } catch (e: Exception) {
            Log.e(TAG, "系统级点击节点失败", e)
            false
        }
    }

    /**
     * 获取系统级当前窗口信息
     */
    fun getSystemCurrentWindowInfo(): String? {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return null
            service.getCurrentWindowInfo()
        } catch (e: Exception) {
            Log.e(TAG, "获取系统级当前窗口信息失败", e)
            null
        }
    }

    /**
     * 获取系统级当前包名
     */
    fun getSystemCurrentPackageName(): String? {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return null
            service.getCurrentPackageName()
        } catch (e: Exception) {
            Log.e(TAG, "获取系统级当前包名失败", e)
            null
        }
    }

    /**
     * 执行系统级全局手势
     */
    fun performSystemGlobalAction(action: Int): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return false
            service.performGlobalAction(action)
        } catch (e: Exception) {
            Log.e(TAG, "执行系统级全局手势失败", e)
            false
        }
    }

    /**
     * 检查SystemAccessibilityService是否可用
     */
    fun isSystemAccessibilityServiceAvailable(): Boolean {
        return try {
            val service = ShizukuServiceManager.getInstance()?.getSystemAccessibilityService() ?: return false
            service.isServiceAvailable()
        } catch (e: Exception) {
            Log.e(TAG, "检查SystemAccessibilityService可用性失败", e)
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
                    dpToPx(),
                    dpToPx(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )

                params.gravity = Gravity.TOP or Gravity.START
                params.x = (x - dpToPx() / 2).toInt()
                params.y = (y - dpToPx() / 2).toInt()

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
    private fun dpToPx(): Int {
        val density = resources.displayMetrics.density
        return (24 * density + 0.5f).toInt()
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
        return currentActivityName ?: currentClassName
    }

    /**
     * 获取当前窗口信息
     */
    fun getCurrentWindowInfo(): String {
        return buildString {
            append("当前窗口信息:\n")
            append("包名: ${currentPackageName ?: "未知"}\n")
            append("类名: ${currentClassName ?: "未知"}\n")
            append("Activity: ${currentActivityName ?: "未知"}\n")
        }
    }

    /**
     * 打印当前窗口信息
     */
    fun printCurrentWindowInfo() {
        Log.d(TAG, getCurrentWindowInfo())
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
