package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnRequestPermissionResultListener
import kotlin.random.Random

class XyzAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "XyzAccessibilityService"
        private var instance: XyzAccessibilityService? = null

        /**
         * 获取无障碍服务实例
         */
        fun getInstance(): XyzAccessibilityService? = instance
    }

    private lateinit var windowManager: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val activeIndicators = mutableListOf<View>()


    // 存储当前窗口/Activity信息
    private var currentPackageName: String? = null
    private var currentClassName: String? = null
    private var currentActivityName: String? = null


    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        instance = this
        Log.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听屏幕变化事件
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 更新当前窗口信息
                currentPackageName = event.packageName?.toString()
                currentClassName = event.className?.toString()

                // 尝试从className中提取Activity名称
                Log.d(TAG, "窗口状态改变:")
                Log.d(TAG, "  包名: $currentPackageName")
                Log.d(TAG, "  类名: $currentClassName")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 窗口内容改变时也可能需要更新信息
                val newPackageName = event.packageName?.toString()
                updatePackageNameIfChanged(newPackageName)
            }
        }
    }

    /**
     * 如果包名发生变化则更新
     */
    private fun updatePackageNameIfChanged(newPackageName: String?) {
        if (currentPackageName != newPackageName) {
            currentPackageName = newPackageName
            Log.d(TAG, "窗口内容改变，包名更新: $currentPackageName")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        // 初始化 Shizuku 服务管理器
        ShizukuServiceManager.getInstance(this)?.initializeShizuku()
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.i(TAG, "开始销毁无障碍服务...")

        // 重置所有状态变量
        try {
            currentPackageName = null
            currentClassName = null
            currentActivityName = null
            Log.i(TAG, "状态变量重置完成")
        } catch (e: Exception) {
            Log.e(TAG, "重置状态变量失败", e)
        }

        // 清理实例引用
        instance = null

        Log.d(TAG, "无障碍服务销毁完成")
    }

    /**
     * 递归打印节点树
     */
    private fun printNodeTree(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null) return

        val indent = "  ".repeat(depth)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val nodeInfo = StringBuilder()
        nodeInfo.append("${indent}├─ ")
        nodeInfo.append("Class: ${node.className ?: "null"}")
        nodeInfo.append(", ID: ${node.viewIdResourceName ?: "null"}")
        nodeInfo.append(", Text: ${node.text ?: "null"}")
        nodeInfo.append(", ContentDesc: ${node.contentDescription ?: "null"}")
        nodeInfo.append(", Bounds: [${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
        nodeInfo.append(", Size: ${bounds.width()}x${bounds.height()}")
        nodeInfo.append(", Clickable: ${node.isClickable}")
        nodeInfo.append(", Enabled: ${node.isEnabled}")
        nodeInfo.append(", Focusable: ${node.isFocusable}")

        Log.d(TAG, nodeInfo.toString())

        // 递归打印子节点
        for (i in 0 until node.childCount) {
            printNodeTree(node.getChild(i), depth + 1)
        }
    }

    /**
     * 根据ID查找节点
     */
    fun findNodeById(id: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByIdRecursive(rootNode, id)
    }

    private fun findNodeByIdRecursive(node: AccessibilityNodeInfo?, id: String): AccessibilityNodeInfo? {
        if (node == null) return null

//        Log.d(TAG, "current resource id: ${node.viewIdResourceName}")

        if (node.viewIdResourceName == id) {
            Log.d(TAG, "find node by id: $id")
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findNodeByIdRecursive(node.getChild(i), id)
            if (result != null) return result
        }

        return null
    }

    /**
     * 根据文本查找节点
     */
    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByTextRecursive(rootNode, text)
    }

    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.text?.toString()?.contains(text) == true || 
            node.contentDescription?.toString()?.contains(text) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findNodeByTextRecursive(node.getChild(i), text)
            if (result != null) return result
        }

        return null
    }

    /**
     * 根据类名查找节点
     */
    fun findNodeByClass(className: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByClassRecursive(rootNode, className)
    }

    private fun findNodeByClassRecursive(node: AccessibilityNodeInfo?, className: String): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.className?.toString()?.contains(className) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val result = findNodeByClassRecursive(node.getChild(i), className)
            if (result != null) return result
        }

        return null
    }

    /**
     * 点击指定节点 - 使用坐标模拟点击
     */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            Log.d(TAG, "节点为空")
            return false
        }

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.isEmpty) {
            Log.d(TAG, "节点边界为空")
            return false
        }

        // 计算节点中心区域的随机点击坐标
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        // 在中心区域的70%范围内随机选择点击点，避免边缘区域
        val rangeX = (bounds.width() * 0.35).toInt()
        val rangeY = (bounds.height() * 0.35).toInt()

        val randomX = centerX + Random.nextInt(-rangeX, rangeX + 1)
        val randomY = centerY + Random.nextInt(-rangeY, rangeY + 1)

        Log.d(TAG, "点击节点坐标: ($randomX, $randomY), 节点边界: $bounds")

        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

}
