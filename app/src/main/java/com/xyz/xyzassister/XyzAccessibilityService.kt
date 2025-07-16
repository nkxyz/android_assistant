package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView

/**
 * 传统无障碍服务
 * 提供基本的无障碍功能和手势支持
 */
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
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 更新当前窗口信息
                currentPackageName = event.packageName?.toString()
                currentClassName = event.className?.toString()

                // 尝试从className中提取Activity名称
                if (currentClassName?.contains(".") == true) {
                    currentActivityName = currentClassName
                }

                Log.d(TAG, "窗口状态改变:")
                Log.d(TAG, "  包名: $currentPackageName")
                Log.d(TAG, "  类名: $currentClassName")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 窗口内容改变时也可能需要更新信息
                val newPackageName = event.packageName?.toString()
                if (currentPackageName != newPackageName) {
                    currentPackageName = newPackageName
                    Log.d(TAG, "窗口内容改变，包名更新: $currentPackageName")
                }
            }
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
    fun printNodeTree(depth: Int = 0) {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            Log.d(TAG, "========== 节点树开始 ==========")
            printNodeTreeRecursive(rootNode, 0)
            Log.d(TAG, "========== 节点树结束 ==========")
        } else {
            Log.w(TAG, "无法获取根节点")
        }
    }

    private fun printNodeTreeRecursive(node: AccessibilityNodeInfo?, depth: Int) {
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

        Log.d(TAG, nodeInfo.toString())

        // 递归打印子节点
        for (i in 0 until node.childCount) {
            printNodeTreeRecursive(node.getChild(i), depth + 1)
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

        if (node.viewIdResourceName == id) {
            Log.d(TAG, "找到节点: $id")
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
     * 点击指定节点 - 使用节点动作
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

        Log.d(TAG, "点击节点: ${node.viewIdResourceName}, 边界: $bounds")

        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 使用手势执行点击
     */
    fun performGestureClick(x: Float, y: Float): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Log.w(TAG, "手势功能需要Android N及以上版本")
            return false
        }

        val path = Path()
        path.moveTo(x, y)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))

        val gesture = gestureBuilder.build()

        var result = false
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                result = true
                Log.d(TAG, "手势点击成功: ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "手势点击被取消: ($x, $y)")
            }
        }

        dispatchGesture(gesture, callback, null)

        // 等待手势完成
        Thread.sleep(100)

        return result
    }

    /**
     * 使用手势执行长按
     */
    fun performGestureLongClick(x: Float, y: Float, duration: Long): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Log.w(TAG, "手势功能需要Android N及以上版本")
            return false
        }

        val path = Path()
        path.moveTo(x, y)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))

        val gesture = gestureBuilder.build()

        var result = false
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                result = true
                Log.d(TAG, "手势长按成功: ($x, $y), 持续时间: ${duration}ms")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "手势长按被取消: ($x, $y)")
            }
        }

        dispatchGesture(gesture, callback, null)

        // 等待手势完成
        Thread.sleep(duration + 100)

        return result
    }

    /**
     * 使用手势执行拖拽
     */
    fun performGestureDrag(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        duration: Long
    ): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) {
            Log.w(TAG, "手势功能需要Android N及以上版本")
            return false
        }

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))

        val gesture = gestureBuilder.build()

        var result = false
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                result = true
                Log.d(TAG, "手势拖拽成功: ($startX, $startY) -> ($endX, $endY)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "手势拖拽被取消")
            }
        }

        dispatchGesture(gesture, callback, null)

        // 等待手势完成
        Thread.sleep(duration + 100)

        return result
    }

    /**
     * 执行全局动作（返回、主页、最近任务等）
     */
    fun performGlobalActionCompat(action: Int): Boolean {
        return performGlobalAction(action)
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

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                append("根节点包名: ${rootNode.packageName}\n")
                append("根节点类名: ${rootNode.className}\n")
                append("子节点数: ${rootNode.childCount}\n")
            } else {
                append("根节点: 不可用\n")
            }
        }
    }

    /**
     * 打印当前窗口信息
     */
    fun printCurrentWindowInfo() {
        Log.d(TAG, getCurrentWindowInfo())
    }
}