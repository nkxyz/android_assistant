package com.xyz.xyzassister

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

/**
 * 抢票业务管理器
 * 支持传统无障碍服务和Shizuku系统服务的统一调用
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

    // 节点信息包装类
    data class NodeInfo(
        val id: String = "",
        val text: String = "",
        val className: String = "",
        val contentDescription: String = "",
        val bounds: Rect = Rect(),
        val isClickable: Boolean = false,
        val isEnabled: Boolean = false,
        val packageName: String = ""
    ) {
        companion object {
            fun fromJson(json: JSONObject): NodeInfo {
                val boundsJson = json.optJSONObject("bounds")
                val bounds = if (boundsJson != null) {
                    Rect(
                        boundsJson.optInt("left"),
                        boundsJson.optInt("top"),
                        boundsJson.optInt("right"),
                        boundsJson.optInt("bottom")
                    )
                } else {
                    Rect()
                }

                return NodeInfo(
                    id = json.optString("id", ""),
                    text = json.optString("text", ""),
                    className = json.optString("className", ""),
                    contentDescription = json.optString("contentDescription", ""),
                    bounds = bounds,
                    isClickable = json.optBoolean("isClickable", false),
                    isEnabled = json.optBoolean("isEnabled", false),
                    packageName = json.optString("packageName", "")
                )
            }

            fun fromAccessibilityNode(node: AccessibilityNodeInfo): NodeInfo {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                return NodeInfo(
                    id = node.viewIdResourceName ?: "",
                    text = node.text?.toString() ?: "",
                    className = node.className?.toString() ?: "",
                    contentDescription = node.contentDescription?.toString() ?: "",
                    bounds = bounds,
                    isClickable = node.isClickable,
                    isEnabled = node.isEnabled,
                    packageName = node.packageName?.toString() ?: ""
                )
            }
        }
    }

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
     */
    private fun executeTicketGrabbingProcess(): Boolean {
        Log.d(TAG, "========== 开始抢票流程 ==========")

        val currentPackage = getCurrentPackageName()
        Log.d(TAG, "当前应用包名: $currentPackage")

        // 点击立即购买按钮
        val startBuyNodeInfo = findNodeInfoById("cn.damai:id/trade_project_detail_purchase_status_bar_container_fl")
        if (startBuyNodeInfo != null && !clickNodeInfo(startBuyNodeInfo)) {
            Log.w(TAG, "点击立即购买按钮失败!")
        }

        // 主循环
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
                    Log.d(TAG, "在抢票页面，继续尝试")

                    // 查找演出日期按钮
                    val dateButtons = findDateButtons()
                    if (dateButtons.isEmpty()) {
                        Log.d(TAG, "未找到演出日期按钮，等待...")
                        waitForPageChange(1000)
                        continue
                    }

                    // 循环选择日期
                    if (dateButtonIndex >= dateButtons.size) {
                        dateButtonIndex = 0
                    }
                    val dateButton = dateButtons[dateButtonIndex++]
                    Log.d(TAG, "点击日期按钮: ${dateButton.text}")
                    if (!clickNodeInfo(dateButton)) {
                        Log.d(TAG, "点击日期按钮失败")
                    }
                    waitForPageChange(500)

                    // 查找可用价位选项
                    val availablePriceOptions = findAvailablePriceOptions()
                    if (availablePriceOptions.isNotEmpty()) {
                        Log.d(TAG, "找到可用价位，点击第一个: ${availablePriceOptions[0].text}")
                        clickNodeInfo(availablePriceOptions[0])
                        waitForPageChange(500)
                    }

                    // 点击购买按钮
                    val buyButtonInfo = findNodeInfoById("cn.damai:id/bottom_layout")
                    if (buyButtonInfo != null) {
                        Log.d(TAG, "点击购买按钮")
                        clickNodeInfo(buyButtonInfo)
                        waitForPageChange(2000)
                    }
                }
                else -> {
                    Log.d(TAG, "未知页面状态，等待...")
                    waitForPageChange(2000)
                    maxRetries--
                }
            }
        }

        if (!isTicketGrabbingActive) {
            Log.d(TAG, "抢票流程被用户停止")
            return false
        }

        Log.d(TAG, "抢票流程完成，未成功抢到票")
        return false
    }

    /**
     * 获取当前包名
     */
    private fun getCurrentPackageName(): String? {
        return try {
            // 优先使用传统无障碍服务
            val xyzAccessibilityService = XyzAccessibilityService.getInstance()
            if (xyzAccessibilityService != null) {
                val rootNode = xyzAccessibilityService.rootInActiveWindow
                return rootNode?.packageName?.toString()
            }

            // 使用Shizuku系统服务
            val shizukuManager = ShizukuServiceManager.getInstance()
            if (shizukuManager?.isShizukuAvailable() == true &&
                shizukuManager.isShizukuPermissionGranted() == true) {
                return shizukuManager.getSystemAccessibilityService()?.getCurrentPackageName()
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "获取当前包名失败", e)
            null
        }
    }

    /**
     * 获取当前Activity
     */
    private fun getCurrentActivity(): String? {
        return try {
            val service = XyzService.getInstance()
            service?.getCurrentActivity()
        } catch (e: Exception) {
            Log.e(TAG, "获取当前Activity失败", e)
            null
        }
    }

    /**
     * 根据ID查找节点信息
     */
    private fun findNodeInfoById(id: String): NodeInfo? {
        return try {
            // 优先使用传统无障碍服务
            val xyzAccessibilityService = XyzAccessibilityService.getInstance()
            if (xyzAccessibilityService != null) {
                val node = xyzAccessibilityService.findNodeById(id)
                return node?.let { NodeInfo.fromAccessibilityNode(it) }
            }

            // 使用Shizuku系统服务
            val shizukuManager = ShizukuServiceManager.getInstance()
            if (shizukuManager?.isShizukuAvailable() == true &&
                shizukuManager.isShizukuPermissionGranted() == true) {
                val jsonStr = shizukuManager.getSystemAccessibilityService()?.findNodeById(id)
                if (!jsonStr.isNullOrEmpty()) {
                    val json = JSONObject(jsonStr)
                    val nodesArray = json.optJSONArray("nodes")
                    if (nodesArray != null && nodesArray.length() > 0) {
                        return NodeInfo.fromJson(nodesArray.getJSONObject(0))
                    }
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "查找节点失败: $id", e)
            null
        }
    }

    /**
     * 点击节点信息
     */
    private fun clickNodeInfo(nodeInfo: NodeInfo): Boolean {
        return try {
            val bounds = nodeInfo.bounds
            if (bounds.isEmpty) {
                Log.w(TAG, "节点边界为空，无法点击")
                return false
            }

            val centerX = bounds.centerX().toFloat()
            val centerY = bounds.centerY().toFloat()

            // 使用XyzService进行点击
            val service = XyzService.getInstance()
            if (service != null) {
                return service.clickAt(centerX, centerY)
            }

            false
        } catch (e: Exception) {
            Log.e(TAG, "点击节点失败", e)
            false
        }
    }

    /**
     * 查找演出日期按钮
     */
    private fun findDateButtons(): List<NodeInfo> {
        Log.d(TAG, "开始查找演出日期按钮...")
        val performFlowLayoutInfo = findNodeInfoById("cn.damai:id/project_detail_perform_flowlayout")
        if (performFlowLayoutInfo == null) {
            Log.d(TAG, "未找到演出日期容器")
            return emptyList()
        }

        // 由于无法直接获取子节点，需要通过其他方式查找
        // 这里需要改进为查找所有可点击的日期按钮
        val dateButtons = mutableListOf<NodeInfo>()

        // TODO: 实现更精确的日期按钮查找逻辑
        Log.d(TAG, "日期按钮查找功能需要完善")

        return dateButtons
    }

    /**
     * 查找可用价位选项
     */
    private fun findAvailablePriceOptions(): List<NodeInfo> {
        Log.d(TAG, "开始查找可用价位选项...")
        val priceFlowLayoutInfo = findNodeInfoById("cn.damai:id/project_detail_perform_price_flowlayout")
        if (priceFlowLayoutInfo == null) {
            Log.d(TAG, "未找到价位选项容器")
            return emptyList()
        }

        // TODO: 实现价位选项查找逻辑
        val availablePriceOptions = mutableListOf<NodeInfo>()

        Log.d(TAG, "价位选项查找功能需要完善")

        return availablePriceOptions
    }

    /**
     * 检查是否在抢票页面
     */
    private fun isInNcovSkuActivity(): Boolean {
        val currentPackage = getCurrentPackageName()
        return currentPackage?.contains("cn.damai") == true &&
                findNodeInfoById("cn.damai:id/project_detail_perform_flowlayout") != null
    }

    /**
     * 检查是否在验证码页面
     */
    private fun isInCaptchaPage(): Boolean {
        val currentActivity = getCurrentActivity()
        return currentActivity?.contains("com.alibaba.wireless.security.open.middletier.fc.ui.ContainerActivity") == true
    }

    /**
     * 检查是否在网络异常页面
     */
    private fun isInNetworkErrorPage(): Boolean {
        return findNodeInfoById("cn.damai:id/state_view_refresh_btn") != null
    }

    /**
     * 检查是否在订单页面
     */
    private fun isInOrderPage(): Boolean {
        val currentActivity = getCurrentActivity()
        return currentActivity?.contains(".ultron.view.activity.DmOrderActivity") == true
    }

    /**
     * 处理验证码页面
     */
    private fun handleCaptchaPage(): Boolean {
        Log.d(TAG, "检测到验证码页面，暂时无法自动处理")
        // TODO: 实现验证码处理逻辑
        return false
    }

    /**
     * 处理网络异常页面
     */
    private fun handleNetworkErrorPage(): Boolean {
        Log.d(TAG, "检测到网络异常页面，点击刷新")
        val refreshButtonInfo = findNodeInfoById("cn.damai:id/state_view_refresh_btn")
        if (refreshButtonInfo != null) {
            clickNodeInfo(refreshButtonInfo)
            return true
        }
        return false
    }

    /**
     * 提交订单
     */
    private fun submitOrder(): Boolean {
        Log.d(TAG, "开始提交订单...")
        // TODO: 实现订单提交逻辑
        return false
    }

    /**
     * 等待页面变化
     */
    private fun waitForPageChange(timeoutMs: Long): Boolean {
        try {
            Thread.sleep(timeoutMs)
            return true
        } catch (e: InterruptedException) {
            Log.e(TAG, "等待页面变化被中断", e)
            return false
        }
    }
}