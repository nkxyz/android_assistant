package com.xyz.xyzassister

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 抢票业务管理器
 * 独立于AccessibilityService和Shizuku连接初始化的业务逻辑模块
 * 可以使用系统无障碍服务或Shizuku权限进行窗口枚举查找及模拟点击
 */
class TicketGrabbingManager {

    companion object {
        private const val TAG = "TicketGrabbingManager"

        @Volatile
        private var instance: TicketGrabbingManager? = null

        fun getInstance(): TicketGrabbingManager {
            return instance ?: synchronized(this) {
                instance ?: TicketGrabbingManager().also { instance = it }
            }
        }
    }

    // 控制抢票流程的标志
    @Volatile
    private var isTicketGrabbingActive = false

    /**
     * 启动抢票流程
     */
    fun startTicketGrabbingProcess(): Boolean {
        isTicketGrabbingActive = true
        return executeTicketGrabbingProcess()
    }

    /**
     * 停止抢票流程
     */
    fun stopTicketGrabbingProcess() {
        Log.d(TAG, "停止抢票流程")
        isTicketGrabbingActive = false
    }

    /**
     * 检查抢票流程是否正在运行
     */
    fun isTicketGrabbingProcessActive(): Boolean {
        return isTicketGrabbingActive
    }

    /**
     * 主要的抢票流程执行逻辑
     * 不依赖于AccessibilityService或Shizuku的连接初始化
     * 系统无障碍或Shizuku权限哪个可用就用哪个进行窗口枚举查找及模拟点击
     */
    private fun executeTicketGrabbingProcess(): Boolean {
        Log.d(TAG, "========== 开始抢票流程 ==========")

        val activityId = getCurrentActivityId()
        Log.d(TAG, "当前Activity ID: $activityId")

        val start_buy = findNodeById("cn.damai:id/trade_project_detail_purchase_status_bar_container_fl")
        if (!clickNode(start_buy)) {
            Log.w(TAG, "click failed!")
        }

        // 处理可能出现的验证码或网络错误
        var maxRetries = 99999999
        var dateButtonIndex = 0
        while (maxRetries > 0 && isTicketGrabbingActive) {
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
                    // 查找演出日期按钮并循环点击
                    val dateButtons = findDateButtons()
                    if (dateButtons.isEmpty()) {
                        Log.d(TAG, "未找到演出日期按钮，流程终止")
                        return false
                    }
                    if (dateButtonIndex >= dateButtons.size) {
                        dateButtonIndex = 0
                    }
                    val dateButton = dateButtons[dateButtonIndex++]
                    Log.d(TAG, "点击日期按钮: ${dateButton.rangeInfo}")
                    if (!clickNode(dateButton)){
                        Log.d(TAG, "点击失败")
                    }
                    if (!waitForPageChange(2000)) {
                        Log.d(TAG, "等待页面变化超时${dateButton.viewIdResourceName}")
                    }
                    // 查找可用价位选项
                    val availablePriceOptions = findAvailablePriceOptions()
                    if (availablePriceOptions.isNotEmpty()) {
                        Log.d(
                            TAG,
                            "找到可用价位，点击第一个: ${availablePriceOptions[0].text}"
                        )
                        clickNode(availablePriceOptions[0])
                        waitForPageChange(3000)
                    }

                    // 点击购买按钮
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

        // 检查是否是因为用户停止而退出循环
        if (!isTicketGrabbingActive) {
            Log.d(TAG, "抢票流程被用户停止")
            return false
        }

        Log.d(TAG, "抢票流程完成，未成功抢到票")
        return false
    }

    /**
     * 获取当前Activity ID
     * 优先使用系统无障碍服务，如果不可用则使用Shizuku服务
     */
    private fun getCurrentActivityId(): String? {
        return try {
            // 优先使用XyzService
            val accessibilityService = XyzService.getInstance()
            if (accessibilityService != null) {
                return accessibilityService.getCurrentActivity()
            }

            // 回退到Shizuku系统服务
            val shizukuManager = ShizukuServiceManager.getInstance()
            if (shizukuManager?.isShizukuAvailable() == true && 
                shizukuManager.isShizukuPermissionGranted() == true) {
                return shizukuManager.getSystemAccessibilityService()?.getCurrentPackageName()
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "获取当前Activity ID失败", e)
            null
        }
    }

    /**
     * 根据ID查找节点
     * 优先使用系统无障碍服务，如果不可用则使用Shizuku服务
     */
    private fun findNodeById(id: String): AccessibilityNodeInfo? {
        return try {
            // 使用Shizuku系统服务 (返回字符串，需要转换)
            val shizukuManager = ShizukuServiceManager.getInstance()
            if (shizukuManager?.isShizukuAvailable() == true && 
                shizukuManager.isShizukuPermissionGranted() == true) {
                val nodeInfo = shizukuManager.getSystemAccessibilityService()?.findNodeById(id)
                // 注意：这里需要将字符串转换为AccessibilityNodeInfo，暂时返回null
                // 实际实现中需要根据具体需求处理
                Log.d(TAG, "Shizuku找到节点: $nodeInfo")
                return null
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "查找节点失败: $id", e)
            null
        }
    }

    /**
     * 点击节点
     * 优先使用系统无障碍服务，如果不可用则使用Shizuku服务
     */
    private fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        return try {
            if (node == null) return false

            // 使用XyzService进行坐标点击
            val service = XyzService.getInstance()
            if (service != null) {
                // 获取节点坐标并使用服务点击
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()
                return service.clickAt(centerX, centerY)
            }

            // 回退到Shizuku服务进行坐标点击
            val shizukuManager = ShizukuServiceManager.getInstance()
            if (shizukuManager?.isShizukuAvailable() == true && 
                shizukuManager.isShizukuPermissionGranted() == true) {
                // 获取节点坐标并使用Shizuku点击
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                val centerX = bounds.centerX().toFloat()
                val centerY = bounds.centerY().toFloat()

                val instrumentationService = shizukuManager.getInstrumentationService()
                return instrumentationService?.click(centerX, centerY) ?: false
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "点击节点失败", e)
            false
        }
    }

    /**
     * 检查当前是否在大麦抢票页面
     */
    fun isInDamaiTicketPage(): Boolean {
        val currentActivity = getCurrentActivityId()
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
     * 获取当前窗口的完整信息（用于调试）
     */
    fun getCurrentWindowInfo(): String {
        val info = StringBuilder()
        info.append("========== 当前窗口信息 ==========\n")

        try {
            val activityId = getCurrentActivityId()
            info.append("Activity ID: ${activityId ?: "null"}\n")

            val service = XyzService.getInstance()
            if (service != null) {
                info.append("XyzService连接状态: 已连接\n")
                val systemWindowInfo = service.getSystemCurrentWindowInfo()
                if (systemWindowInfo != null) {
                    info.append("系统窗口信息: $systemWindowInfo\n")
                }
            } else {
                info.append("XyzService连接状态: 未连接\n")
            }
        } catch (e: Exception) {
            info.append("获取窗口信息时出错: ${e.message}\n")
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
            // 尝试从XyzService获取当前Activity信息
            val service = XyzService.getInstance()
            if (service != null) {
                val currentActivity = service.getCurrentActivity()
                if (currentActivity != null) {
                    return currentActivity
                }
            }

            // 尝试从Shizuku系统服务获取包名
            val shizukuManager = ShizukuServiceManager.getInstance()
            if (shizukuManager?.isShizukuAvailable() == true && 
                shizukuManager.isShizukuPermissionGranted() == true) {
                val packageName = shizukuManager.getSystemAccessibilityService()?.getCurrentPackageName()
                if (packageName != null) {
                    return packageName
                }
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
//                val hasSoldOutTag = checkForSoldOutTag(child)
                val hasSoldOutTag = checkChildNodeIdsAndTexts(child,
                    listOf(
                        NodeSearchIdAndText("cn.damai:id/layout_tag", "缺货登记"),
                        NodeSearchIdAndText("cn.damai:id/layout_tag", "可预约")
                    )
                )
                if (!hasSoldOutTag) {
                    availablePriceOptions.add(child)
                    Log.d(TAG, "找到可用价位: ${child.rangeInfo}")
                } else {
                    Log.d(TAG, "价位已售罄: ${child.rangeInfo}")
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

    private fun checkChildNodeIdAndText(node: AccessibilityNodeInfo, id: String, text: String): Boolean {
        Log.d(TAG, "枚举控件: ${node.viewIdResourceName}, ${node.text} ")
        if (node.viewIdResourceName == id && node.text?.toString() == text) {
            Log.d(TAG, "命中控件: ${node.viewIdResourceName}, ${node.text} ")
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (checkChildNodeIdAndText(child, id, text)) {
                    return true
                }
            }
        }
        return false
    }

    data class NodeSearchIdAndText(val id: String, val text: String) {
        override fun toString(): String {
            return "[$id, $text]"
        }
    }

    private fun checkChildNodeIdsAndTexts(node: AccessibilityNodeInfo, targets: List<NodeSearchIdAndText>): Boolean {
        for (target in targets) {
            if (checkChildNodeIdAndText(node, target.id, target.text)) {
                return true
            }
        }
        return false
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

    // Stub implementations for missing methods
    private fun findNodesByXPath(xpath: String): List<AccessibilityNodeInfo> {
        Log.w(TAG, "findNodesByXPath: 功能暂未实现")
        return emptyList()
    }

    private fun performSliderCaptcha(container: AccessibilityNodeInfo, button: AccessibilityNodeInfo): Boolean {
        Log.w(TAG, "performSliderCaptcha: 功能暂未实现")
        return false
    }

    private fun findAllNodesByCriteria(criteria: NodeSearchCriteria): List<AccessibilityNodeInfo> {
        Log.w(TAG, "findAllNodesByCriteria: 功能暂未实现")
        return emptyList()
    }

    // Stub data class for NodeSearchCriteria
    private data class NodeSearchCriteria(
        val className: String? = null,
        val text: String? = null,
        val id: String? = null
    )
}
