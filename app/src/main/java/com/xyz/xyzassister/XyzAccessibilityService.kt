package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class XyzAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "XyzAccessibilityService"
        var instance: XyzAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监听屏幕变化事件
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    Log.d(TAG, "窗口状态改变: ${it.packageName}")
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    Log.d(TAG, "窗口内容改变: ${it.packageName}")
                }
                else -> {
                    // 其他事件类型
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "无障碍服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "无障碍服务已销毁")
    }

    /**
     * 打印当前屏幕所有控件信息到logcat
     */
    fun printScreenElements() {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            Log.d(TAG, "========== 屏幕控件树结构 ==========")
            printNodeTree(rootNode, 0)
            Log.d(TAG, "========== 控件树结构结束 ==========")
        } else {
            Log.d(TAG, "无法获取屏幕根节点")
        }
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

        if (node.viewIdResourceName == id) {
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
     * 点击指定节点
     */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null || !node.isClickable) {
            Log.d(TAG, "节点不可点击")
            return false
        }

        return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * 点击指定坐标
     */
    fun clickAt(x: Float, y: Float): Boolean {
        val path = Path()
        path.moveTo(x, y)

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, 100)
        gestureBuilder.addStroke(strokeDescription)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * 拖拽操作
     */
    fun dragFromTo(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500): Boolean {
        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        gestureBuilder.addStroke(strokeDescription)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * 滑动操作
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        return dragFromTo(startX, startY, endX, endY, duration)
    }
}
