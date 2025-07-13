package com.xyz.xyzassister

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import kotlin.random.Random
import java.util.concurrent.Executors

/**
 * 控件查找条件类
 */
data class NodeSearchCriteria(
    val id: String? = null,
    val text: String? = null,
    val className: String? = null,
    val contentDescription: String? = null,
    val isClickable: Boolean? = null,
    val isEnabled: Boolean? = null,
    val textContains: String? = null,
    val classContains: String? = null
)

class XyzAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "XyzAccessibilityService"
        var instance: XyzAccessibilityService? = null
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
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "无障碍服务已连接")
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
                currentActivityName = extractActivityName(currentClassName)

                Log.d(TAG, "窗口状态改变:")
                Log.d(TAG, "  包名: $currentPackageName")
                Log.d(TAG, "  类名: $currentClassName")
                Log.d(TAG, "  Activity: $currentActivityName")
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 窗口内容改变时也可能需要更新信息
                val newPackageName = event.packageName?.toString()
                updatePackageNameIfChanged(newPackageName)
            }
        }
    }

    /**
     * 从类名中提取Activity名称
     */
    private fun extractActivityName(className: String?): String? {
        return if (className != null && className.contains(".")) {
            className.substringAfterLast(".")
        } else {
            className
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

    override fun onDestroy() {
        super.onDestroy()
        // 清除所有点击指示器
        clearAllClickIndicators()
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

        // 显示点击指示器
        showClickIndicator(randomX.toFloat(), randomY.toFloat())

        return clickAt(randomX.toFloat(), randomY.toFloat())
    }

    /**
     * 点击指定坐标 - 使用 dispatchGesture 方式
     */
    fun clickAt(x: Float, y: Float): Boolean {
        return clickAtWithGesture(x, y)
    }

    /**
     * 使用 dispatchGesture 进行点击
     */
    private fun clickAtWithGesture(x: Float, y: Float): Boolean {
        return try {
            val path = Path()
            path.moveTo(x, y)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, 100)
            gestureBuilder.addStroke(strokeDescription)

            val result = dispatchGesture(gestureBuilder.build(), null, null)
            Log.d(TAG, "dispatchGesture 点击完成: ($x, $y), 结果: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture 点击失败", e)
            false
        }
    }

    /**
     * 拖拽操作 - 使用 dispatchGesture 方式
     */
    fun dragFromTo(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 500): Boolean {
        return dragFromToWithGesture(startX, startY, endX, endY, duration)
    }

    /**
     * 使用 dispatchGesture 进行拖拽
     */
    private fun dragFromToWithGesture(
        startX: Float, 
        startY: Float, 
        endX: Float, 
        endY: Float, 
        duration: Long = 500
    ): Boolean {
        return try {
            val path = Path()
            path.moveTo(startX, startY)
            path.lineTo(endX, endY)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)

            val result = dispatchGesture(gestureBuilder.build(), null, null)
            Log.d(TAG, "dispatchGesture 拖拽完成: ($startX, $startY) -> ($endX, $endY), 结果: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture 拖拽失败", e)
            false
        }
    }

    /**
     * 滑动操作
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300): Boolean {
        return dragFromTo(startX, startY, endX, endY, duration)
    }

    /**
     * 长按操作 - 使用 dispatchGesture 方式
     */
    fun longClickAt(x: Float, y: Float, duration: Long = 1000): Boolean {
        return longClickAtWithGesture(x, y, duration)
    }

    /**
     * 使用 dispatchGesture 进行长按
     */
    private fun longClickAtWithGesture(x: Float, y: Float, duration: Long = 1000): Boolean {
        return try {
            val path = Path()
            path.moveTo(x, y)

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
            gestureBuilder.addStroke(strokeDescription)

            val result = dispatchGesture(gestureBuilder.build(), null, null)
            Log.d(TAG, "dispatchGesture 长按完成: ($x, $y), 持续时间: ${duration}ms, 结果: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture 长按失败", e)
            false
        }
    }

    /**
     * 双击操作 - 使用 dispatchGesture 方式
     */
    fun doubleClickAt(x: Float, y: Float): Boolean {
        return doubleClickAtWithGesture(x, y)
    }

    /**
     * 使用 dispatchGesture 进行双击
     */
    private fun doubleClickAtWithGesture(x: Float, y: Float): Boolean {
        return try {
            // 第一次点击
            val firstClick = clickAtWithGesture(x, y)
            if (!firstClick) {
                Log.e(TAG, "双击的第一次点击失败")
                return false
            }

            // 等待双击间隔
            Thread.sleep(100)

            // 第二次点击
            val secondClick = clickAtWithGesture(x, y)
            if (!secondClick) {
                Log.e(TAG, "双击的第二次点击失败")
                return false
            }

            Log.d(TAG, "dispatchGesture 双击完成: ($x, $y)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture 双击失败", e)
            false
        }
    }

    /**
     * 显示点击指示器
     */
    private fun showClickIndicator(x: Float, y: Float) {
        // 确保在主线程中执行UI操作
        handler.post {
            try {
                val indicator = ImageView(this)
                indicator.setImageResource(R.drawable.click_indicator)

                val layoutParams = WindowManager.LayoutParams().apply {
                    width = 40.dpToPx()
                    height = 40.dpToPx()
                    type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.TOP or Gravity.START
                    this.x = (x - 20.dpToPx() / 2).toInt()
                    this.y = (y - 20.dpToPx() / 2).toInt()
                }

                windowManager.addView(indicator, layoutParams)
                activeIndicators.add(indicator)

                // 1秒后自动移除指示器
                handler.postDelayed({
                    removeClickIndicator(indicator)
                }, 1000)

                Log.d(TAG, "显示点击指示器在坐标: ($x, $y)")
            } catch (e: Exception) {
                Log.e(TAG, "显示点击指示器失败", e)
            }
        }
    }

    /**
     * 移除点击指示器
     */
    private fun removeClickIndicator(indicator: View) {
        // 确保在主线程中执行UI操作
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
     * 清除所有点击指示器
     */
    private fun clearAllClickIndicators() {
        // 确保在主线程中执行UI操作
        handler.post {
            try {
                activeIndicators.forEach { indicator ->
                    try {
                        windowManager.removeView(indicator)
                    } catch (e: Exception) {
                        Log.e(TAG, "移除指示器时出错", e)
                    }
                }
                activeIndicators.clear()
            } catch (e: Exception) {
                Log.e(TAG, "清除所有指示器失败", e)
            }
        }
    }

    /**
     * dp转px的扩展函数
     */
    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    /**
     * 根据多个条件查找所有匹配的节点
     */
    fun findAllNodesByCriteria(
        criteria: NodeSearchCriteria,
        parentNode: AccessibilityNodeInfo? = null
    ): List<AccessibilityNodeInfo> {
        val rootNode = parentNode ?: rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeInfo>()
        findAllNodesByCriteriaRecursive(rootNode, criteria, results)
        return results
    }

    private fun findAllNodesByCriteriaRecursive(
        node: AccessibilityNodeInfo?,
        criteria: NodeSearchCriteria,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        // 检查当前节点是否匹配所有条件
        var matches = true

        // 检查ID
        criteria.id?.let { targetId ->
            if (node.viewIdResourceName != targetId) {
                matches = false
            }
        }

        // 检查精确文本
        criteria.text?.let { targetText ->
            if (node.text?.toString() != targetText) {
                matches = false
            }
        }

        // 检查文本包含
        criteria.textContains?.let { targetText ->
            if (node.text?.toString()?.contains(targetText) != true &&
                node.contentDescription?.toString()?.contains(targetText) != true) {
                matches = false
            }
        }

        // 检查精确类名
        criteria.className?.let { targetClass ->
            if (node.className?.toString() != targetClass) {
                matches = false
            }
        }

        // 检查类名包含
        criteria.classContains?.let { targetClass ->
            if (node.className?.toString()?.contains(targetClass) != true) {
                matches = false
            }
        }

        // 检查内容描述
        criteria.contentDescription?.let { targetDesc ->
            if (node.contentDescription?.toString() != targetDesc) {
                matches = false
            }
        }

        // 检查可点击性
        criteria.isClickable?.let { targetClickable ->
            if (node.isClickable != targetClickable) {
                matches = false
            }
        }

        // 检查启用状态
        criteria.isEnabled?.let { targetEnabled ->
            if (node.isEnabled != targetEnabled) {
                matches = false
            }
        }

        // 如果匹配所有条件，添加到结果中
        if (matches) {
            results.add(node)
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            findAllNodesByCriteriaRecursive(node.getChild(i), criteria, results)
        }
    }

    /**
     * 根据ID查找所有匹配的节点
     */
    fun findAllNodesById(id: String, parentNode: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        return findAllNodesByCriteria(NodeSearchCriteria(id = id), parentNode)
    }

    /**
     * 根据文本查找所有匹配的节点
     */
    fun findAllNodesByText(text: String, parentNode: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        return findAllNodesByCriteria(NodeSearchCriteria(textContains = text), parentNode)
    }

    /**
     * 根据类名查找所有匹配的节点
     */
    fun findAllNodesByClass(className: String, parentNode: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        return findAllNodesByCriteria(NodeSearchCriteria(classContains = className), parentNode)
    }

    /**
     * 类似XPath的查找方法
     * 支持简单的路径查找，如: "LinearLayout/TextView" 或 "//Button"
     */
    fun findNodesByXPath(xpath: String, parentNode: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val rootNode = parentNode ?: rootInActiveWindow ?: return emptyList()

        return when {
            xpath.startsWith("//") -> {
                // 全局查找，如 "//Button"
                val className = xpath.substring(2)
                findAllNodesByClass(className, rootNode)
            }
            xpath.contains("/") -> {
                // 路径查找，如 "LinearLayout/TextView"
                findNodesByPath(xpath.split("/"), rootNode)
            }
            else -> {
                // 单个类名查找
                findAllNodesByClass(xpath, rootNode)
            }
        }
    }

    /**
     * 根据路径查找节点
     */
    private fun findNodesByPath(pathParts: List<String>, rootNode: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        if (pathParts.isEmpty()) return listOf(rootNode)

        val currentClassName = pathParts[0]
        val remainingPath = pathParts.drop(1)
        val results = mutableListOf<AccessibilityNodeInfo>()

        // 如果当前节点匹配第一个路径部分
        if (rootNode.className?.toString()?.contains(currentClassName) == true) {
            if (remainingPath.isEmpty()) {
                // 这是路径的最后一部分
                results.add(rootNode)
            } else {
                // 继续在子节点中查找剩余路径
                for (i in 0 until rootNode.childCount) {
                    results.addAll(findNodesByPath(remainingPath, rootNode.getChild(i)))
                }
            }
        }

        // 在子节点中查找完整路径
        for (i in 0 until rootNode.childCount) {
            results.addAll(findNodesByPath(pathParts, rootNode.getChild(i)))
        }

        return results
    }

    /**
     * 高级查找方法 - 支持属性选择器
     * 例如: findNodesBySelector("Button[text='确定'][clickable='true']")
     */
    fun findNodesBySelector(selector: String, parentNode: AccessibilityNodeInfo? = null): List<AccessibilityNodeInfo> {
        val criteria = parseSelector(selector)
        return findAllNodesByCriteria(criteria, parentNode)
    }

    /**
     * 解析选择器字符串
     */
    private fun parseSelector(selector: String): NodeSearchCriteria {
        var className: String? = null
        var id: String? = null
        var text: String? = null
        var textContains: String? = null
        var contentDescription: String? = null
        var isClickable: Boolean? = null
        var isEnabled: Boolean? = null

        // 提取类名（选择器开头的部分）
        val classMatch = Regex("^([A-Za-z]+)").find(selector)
        className = classMatch?.groupValues?.get(1)

        // 提取属性
        val attributePattern = Regex("\\[([^=]+)='([^']+)'\\]")
        attributePattern.findAll(selector).forEach { match ->
            val attrName = match.groupValues[1]
            val attrValue = match.groupValues[2]

            when (attrName) {
                "id" -> id = attrValue
                "text" -> text = attrValue
                "textContains" -> textContains = attrValue
                "contentDescription" -> contentDescription = attrValue
                "clickable" -> isClickable = attrValue.toBoolean()
                "enabled" -> isEnabled = attrValue.toBoolean()
            }
        }

        return NodeSearchCriteria(
            id = id,
            text = text,
            className = className,
            contentDescription = contentDescription,
            isClickable = isClickable,
            isEnabled = isEnabled,
            textContains = textContains,
            classContains = if (className != null) className else null
        )
    }

    /**
     * 在指定父容器内查找控件的便捷方法
     */
    fun findInContainer(containerId: String, searchCriteria: NodeSearchCriteria): List<AccessibilityNodeInfo> {
        val container = findNodeById(containerId)
        return if (container != null) {
            findAllNodesByCriteria(searchCriteria, container)
        } else {
            emptyList()
        }
    }

    /**
     * 打印查找结果的详细信息
     */
    fun printSearchResults(nodes: List<AccessibilityNodeInfo>, tag: String = "SearchResult") {
        Log.d(TAG, "========== $tag (${nodes.size} 个结果) ==========")
        nodes.forEachIndexed { index, node ->
            val bounds = Rect()
            node.getBoundsInScreen(bounds)

            Log.d(TAG, "[$index] Class: ${node.className}")
            Log.d(TAG, "     ID: ${node.viewIdResourceName}")
            Log.d(TAG, "     Text: ${node.text}")
            Log.d(TAG, "     ContentDesc: ${node.contentDescription}")
            Log.d(TAG, "     Bounds: [${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
            Log.d(TAG, "     Clickable: ${node.isClickable}, Enabled: ${node.isEnabled}")
        }
        Log.d(TAG, "========== $tag 结束 ==========")
    }

    // ==================== 抢票自动化功能 ====================

    /**
     * 获取当前Activity信息（包名）
     */
    fun getCurrentActivity(): String? {
        try {
            // 检查服务是否已连接
            if (!isServiceConnected()) {
                Log.w(TAG, "无障碍服务未连接")
                return null
            }

            // 优先使用存储的包名信息
            if (currentPackageName != null) {
                return currentPackageName
            }

            // 如果存储的信息为空，尝试从当前窗口获取
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "无法获取当前窗口信息")
                return null
            }

            val packageName = rootNode.packageName?.toString()
            currentPackageName = packageName
            return packageName
        } catch (e: Exception) {
            Log.e(TAG, "获取当前Activity时发生错误", e)
            return null
        }
    }

    /**
     * 检查服务是否已连接
     */
    private fun isServiceConnected(): Boolean {
        return try {
            // 尝试访问服务信息来检查连接状态
            serviceInfo != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查当前是否在大麦抢票页面
     */
    fun isInDamaiTicketPage(): Boolean {
        val currentActivity = getCurrentActivity()
        Log.d(TAG, "当前Activity: $currentActivity")
        return currentActivity?.contains("cn.damai") == true
    }

    /**
     * 检查当前是否在指定的NcovSkuActivity页面
     */
    fun isInNcovSkuActivity(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        // 通过检查特定控件来判断是否在正确页面
        val activityId = getCurrentActivityId()
        Log.d(TAG, "当前Activity ID: $activityId")

        val performFlowLayout = findNodeById("cn.damai:id/project_detail_perform_flowlayout")
//        val priceFlowLayout = findNodeById("cn.damai:id/project_detail_perform_price_flowlayout")
        return performFlowLayout != null //&& priceFlowLayout != null
    }

    /**
     * 获取当前Activity的ID/名称（公共方法）
     */
    fun getCurrentActivityId(): String? {
        return getCurrentActivityIdInternal()
    }

    /**
     * 获取当前窗口的完整信息（用于调试）
     */
    fun getCurrentWindowInfo(): String {
        val info = StringBuilder()
        info.append("========== 当前窗口信息 ==========\n")
        info.append("包名: ${currentPackageName ?: "null"}\n")
        info.append("类名: ${currentClassName ?: "null"}\n")
        info.append("Activity: ${currentActivityName ?: "null"}\n")
        info.append("服务连接状态: ${if (isServiceConnected()) "已连接" else "未连接"}\n")

        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                info.append("根节点包名: ${rootNode.packageName ?: "null"}\n")
                info.append("根节点类名: ${rootNode.className ?: "null"}\n")
                info.append("根节点ID: ${rootNode.viewIdResourceName ?: "null"}\n")
                info.append("根节点文本: ${rootNode.text ?: "null"}\n")
            } else {
                info.append("根节点: null\n")
            }
        } catch (e: Exception) {
            info.append("获取根节点信息时出错: ${e.message}\n")
        }

        info.append("=====================================")
        return info.toString()
    }

    /**
     * 打印当前窗口信息到日志
     */
    fun printCurrentWindowInfo() {
        val info = getCurrentWindowInfo()
        Log.d(TAG, info)
    }

    /**
     * 获取当前Activity的ID/名称（内部实现）
     */
    private fun getCurrentActivityIdInternal(): String? {
        try {
            // 检查服务是否已连接
            if (!isServiceConnected()) {
                Log.w(TAG, "无障碍服务未连接")
                return null
            }

            // 优先返回存储的Activity名称
            if (currentActivityName != null) {
                return currentActivityName
            }

            // 如果Activity名称为空，返回类名
            if (currentClassName != null) {
                return currentClassName
            }

            // 如果都为空，尝试从当前窗口获取
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val className = rootNode.className?.toString()
                currentClassName = className
                currentActivityName = extractActivityName(className)
                return currentActivityName ?: className
            }

            Log.w(TAG, "无法获取当前Activity ID")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "获取当前Activity ID时发生错误", e)
            return null
        }
    }

    /**
     * 查找演出日期选项按钮
     */
    fun findDateButtons(): List<AccessibilityNodeInfo> {
        Log.d(TAG, "开始查找演出日期按钮...")
        val performFlowLayout = findNodeById("cn.damai:id/project_detail_perform_flowlayout")
        if (performFlowLayout == null) {
            Log.d(TAG, "未找到演出日期容器")
            return emptyList()
        }

        val dateButtons = mutableListOf<AccessibilityNodeInfo>()
        // 查找一级子控件中所有可点击的控件
        for (i in 0 until performFlowLayout.childCount) {
            val child = performFlowLayout.getChild(i)
            if (child != null && child.isClickable) {
                dateButtons.add(child)
                Log.d(TAG, "找到日期按钮: ${child.text}, ID: ${child.viewIdResourceName}")
            }
        }

        Log.d(TAG, "共找到 ${dateButtons.size} 个日期按钮")
        return dateButtons
    }

    /**
     * 查找价位选项按钮（排除已售罄的）
     */
    fun findAvailablePriceOptions(): List<AccessibilityNodeInfo> {
        Log.d(TAG, "开始查找可用价位选项...")
        val priceFlowLayout = findNodeById("cn.damai:id/project_detail_perform_price_flowlayout")
        if (priceFlowLayout == null) {
            Log.d(TAG, "未找到价位选项容器")
            return emptyList()
        }

        val availablePriceOptions = mutableListOf<AccessibilityNodeInfo>()

        // 查找一级子控件中所有可点击的控件
        for (i in 0 until priceFlowLayout.childCount) {
            val child = priceFlowLayout.getChild(i)
            if (child != null && child.isClickable) {
                // 检查该控件的子控件中是否存在售罄标签
                val hasSoldOutTag = checkForSoldOutTag(child)
                if (!hasSoldOutTag) {
                    availablePriceOptions.add(child)
                    Log.d(TAG, "找到可用价位: ${child.text}, ID: ${child.viewIdResourceName}")
                } else {
                    Log.d(TAG, "价位已售罄: ${child.text}")
                }
            }
        }

        Log.d(TAG, "共找到 ${availablePriceOptions.size} 个可用价位")
        return availablePriceOptions
    }

    /**
     * 检查控件是否包含售罄标签
     */
    private fun checkForSoldOutTag(node: AccessibilityNodeInfo): Boolean {
        return checkForSoldOutTagRecursive(node)
    }

    private fun checkForSoldOutTagRecursive(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        // 检查当前节点是否为售罄标签
        if (node.viewIdResourceName == "cn.damai:id/layout_tag") {
            return true
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            if (checkForSoldOutTagRecursive(node.getChild(i))) {
                return true
            }
        }

        return false
    }

    /**
     * 等待页面变化
     */
    fun waitForPageChange(timeoutMs: Long = 5000): Boolean {
        Log.d(TAG, "等待页面变化...")
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                Thread.sleep(500)
                // 检查页面是否有变化
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    Log.d(TAG, "页面已变化")
                    return true
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "等待页面变化被中断", e)
                return false
            }
        }

        Log.d(TAG, "等待页面变化超时")
        return false
    }

    /**
     * 检查是否进入验证码页面
     */
    fun isInCaptchaPage(): Boolean {
        val currentActivity = getCurrentActivity()
        return currentActivity?.contains("com.alibaba.wireless.security.open.middletier.fc.ui.ContainerActivity") == true
    }

    /**
     * 处理验证码页面
     */
    fun handleCaptchaPage(): Boolean {
        Log.d(TAG, "检测到验证码页面，开始处理...")

        // 检查是否有重试按钮
        val retryButton = findNodesByXPath("//*[@resource-id=\"nc_1_refresh1\"]//*[contains(@text,\"重试\")]")
        if (retryButton.isNotEmpty()) {
            Log.d(TAG, "找到重试按钮，点击重试")
            clickNode(retryButton[0])
            waitForPageChange()
            return true
        }

        // 检查是否有滑块验证
        val sliderContainer = findNodeById("nc_1_n1t")
        val sliderButton = findNodeById("nc_1_n1z")

        if (sliderContainer != null && sliderButton != null) {
            Log.d(TAG, "找到滑块验证，开始滑动")
            return performSliderCaptcha(sliderContainer, sliderButton)
        }

        Log.d(TAG, "未找到可处理的验证码元素")
        return false
    }

    /**
     * 执行滑块验证（模拟人类行为）
     */
    fun performSliderCaptcha(container: AccessibilityNodeInfo, button: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "开始执行滑块验证...")

        val containerBounds = Rect()
        container.getBoundsInScreen(containerBounds)

        val buttonBounds = Rect()
        button.getBoundsInScreen(buttonBounds)

        // 计算滑动距离
        val startX = buttonBounds.centerX().toFloat()
        val startY = buttonBounds.centerY().toFloat()
        val endX = containerBounds.right - buttonBounds.width() / 2f
        val endY = startY

        Log.d(TAG, "滑块起始位置: ($startX, $startY)")
        Log.d(TAG, "滑块结束位置: ($endX, $endY)")

        // 模拟人类滑动行为：带有加速度和随机弧线
        return performHumanLikeSlide(startX, startY, endX, endY)
    }

    /**
     * 模拟人类滑动行为
     */
    private fun performHumanLikeSlide(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val path = Path()
        path.moveTo(startX, startY)

        val distance = endX - startX
        val duration = 1000L + (Math.random() * 500).toLong() // 1-1.5秒随机时长

        // 添加随机弧线和加速度变化
        val controlPoints = 5
        for (i in 1..controlPoints) {
            val progress = i.toFloat() / controlPoints
            val x = startX + distance * progress

            // 添加随机Y轴偏移模拟手抖
            val yOffset = (Math.random() * 10 - 5).toFloat()
            val y = startY + yOffset

            path.lineTo(x, y)
        }

        path.lineTo(endX, endY)

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(path, 0, duration)
        gestureBuilder.addStroke(strokeDescription)

        Log.d(TAG, "执行人类化滑动，时长: ${duration}ms")
        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * 检查是否在网络异常页面
     */
    fun isInNetworkErrorPage(): Boolean {
        val refreshButton = findNodeById("cn.damai:id/state_view_refresh_btn")
        return refreshButton != null
    }

    /**
     * 处理网络异常页面
     */
    fun handleNetworkErrorPage(): Boolean {
        Log.d(TAG, "检测到网络异常页面，点击刷新")
        val refreshButton = findNodeById("cn.damai:id/state_view_refresh_btn")
        if (refreshButton != null) {
            clickNode(refreshButton)
            waitForPageChange()
            return true
        }
        return false
    }

    /**
     * 检查是否在订单页面
     */
    fun isInOrderPage(): Boolean {
        val currentActivity = getCurrentActivity()
        return currentActivity?.contains(".ultron.view.activity.DmOrderActivity") == true
    }

    /**
     * 提交订单
     */
    fun submitOrder(): Boolean {
        Log.d(TAG, "开始提交订单...")
        val submitButton = findAllNodesByCriteria(
            NodeSearchCriteria(
                className = "android.widget.TextView",
                text = "立即提交"
            )
        )

        if (submitButton.isNotEmpty()) {
            Log.d(TAG, "找到提交按钮，点击提交")
            return clickNode(submitButton[0])
        }

        Log.d(TAG, "未找到提交按钮")
        return false
    }

    /**
     * 主要的抢票流程
     */
    fun startTicketGrabbingProcess(): Boolean {
        Log.d(TAG, "========== 开始抢票流程 ==========")

        // 1. 检查当前是否在正确页面
//        if (!isInNcovSkuActivity()) {
//            Log.d(TAG, "当前不在抢票页面，流程终止")
//            return false
//        }

        val activityId = getCurrentActivityId()
        Log.d(TAG, "当前Activity ID: $activityId")
        val start_buy = findNodeById("cn.damai:id/trade_project_detail_purchase_status_bar_container_fl")
        if (!clickNode(start_buy)) {
            Log.w(TAG, "click failed!")
        }

        // 5. 处理可能出现的验证码或网络错误
        var maxRetries = 99999999
        var dateButtonIndex = 0
        while (maxRetries > 0) {
            when {
                isInCaptchaPage() -> {
                    Log.d(TAG, "进入验证码页面")
                    if (handleCaptchaPage()) {
                        waitForPageChange(3000)
                    } else {
                        maxRetries--
                    }
                }
                isInNetworkErrorPage() -> {
                    Log.d(TAG, "进入网络错误页面")
                    if (handleNetworkErrorPage()) {
                        waitForPageChange(3000)
                    } else {
                        maxRetries--
                    }
                }
                isInOrderPage() -> {
                    Log.d(TAG, "成功进入订单页面")
                    return submitOrder()
                }
                isInNcovSkuActivity() -> {
                    Log.d(TAG, "返回到抢票页面，继续尝试")
                    // 2. 查找演出日期按钮并循环点击
                    val dateButtons = findDateButtons()
                    if (dateButtons.isEmpty()) {
                        Log.d(TAG, "未找到演出日期按钮，流程终止")
                        return false
                    }
                    if (dateButtonIndex >= dateButtons.size) {
                        dateButtonIndex = 0
                    }
                    val dateButton = dateButtons[dateButtonIndex]
                    Log.d(TAG, "点击日期按钮: ${dateButton.rangeInfo}")
                    if (!clickNode(dateButton)){
                        Log.d(TAG, "点击失败")
                    }
                    if (!waitForPageChange(2000)) {
                        Log.d(TAG, "等待页面变化超时${dateButton.viewIdResourceName}")
                    }
                    // 3. 查找可用价位选项
                    val availablePriceOptions = findAvailablePriceOptions()
                    if (availablePriceOptions.isNotEmpty()) {
                        Log.d(
                            TAG,
                            "找到可用价位，点击第一个: ${availablePriceOptions[0].text}"
                        )
                        clickNode(availablePriceOptions[0])
                        waitForPageChange(3000)
                    }

                    // 4. 点击购买按钮
                    val buyButton = findNodeById("cn.damai:id/bottom_layout")
                    if (buyButton != null) {
                        Log.d(TAG, "点击购买按钮")
                        clickNode(buyButton)
                        waitForPageChange(5000)
                    }
                }
                else -> {
                    Log.d(TAG, "未知页面状态，等待...")
                    waitForPageChange(2000)
                    maxRetries--
                }
            }
        }


        Log.d(TAG, "抢票流程完成，未成功抢到票")
        return false
    }
}
