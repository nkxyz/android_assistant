package com.xyz.xyzassister

import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku服务管理器
 * 负责管理Shizuku相关的服务绑定和使用，独立于系统无障碍服务
 */
class ShizukuServiceManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuServiceManager"
        
        @Volatile
        private var instance: ShizukuServiceManager? = null
        
        fun getInstance(context: Context): ShizukuServiceManager {
            return instance ?: synchronized(this) {
                instance ?: ShizukuServiceManager(context.applicationContext).also { instance = it }
            }
        }
        
        fun getInstance(): ShizukuServiceManager? = instance
    }

    // Shizuku 相关字段
    private var instrumentation: Instrumentation? = null
    private var isShizukuAvailable: Boolean = false
    private var isShizukuPermissionGranted: Boolean = false

    // Binder 服务相关字段
    private var instrumentationService: IInstrumentationService? = null
    private var systemAccessibilityService: IAccessibilityService? = null
    private var serviceConnection: ServiceConnection? = null
    private var accessibilityServiceConnection: ServiceConnection? = null
    private var userServiceArgs: Shizuku.UserServiceArgs? = null
    private var accessibilityUserServiceArgs: Shizuku.UserServiceArgs? = null

    // Shizuku 事件监听器
    private val shizukuPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        Log.d(TAG, "Shizuku 权限请求结果: requestCode=$requestCode, grantResult=$grantResult")
        isShizukuPermissionGranted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (isShizukuPermissionGranted) {
            initializeShizukuBinder()
        }
    }

    private val shizukuBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku Binder 已接收")
        isShizukuAvailable = true
        checkShizukuPermission()
    }

    private val shizukuBinderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku Binder 已断开")
        isShizukuAvailable = false
        isShizukuPermissionGranted = false
        instrumentation = null
        instrumentationService = null
        systemAccessibilityService = null
    }

    /**
     * 初始化 Shizuku 服务
     */
    fun initializeShizuku() {
        try {
            // 添加 Shizuku 监听器
            Shizuku.addRequestPermissionResultListener(shizukuPermissionResultListener)
            Shizuku.addBinderReceivedListener(shizukuBinderReceivedListener)
            Shizuku.addBinderDeadListener(shizukuBinderDeadListener)

            // 检查 Shizuku 是否可用
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku 服务可用")
                isShizukuAvailable = true
                checkShizukuPermission()
            } else {
                Log.d(TAG, "Shizuku 服务不可用")
                isShizukuAvailable = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化 Shizuku 失败", e)
            isShizukuAvailable = false
        }
    }

    /**
     * 检查并请求 Shizuku 权限
     */
    private fun checkShizukuPermission() {
        try {
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Shizuku 权限已授予")
                isShizukuPermissionGranted = true
                initializeShizukuBinder()
            } else {
                Log.d(TAG, "请求 Shizuku 权限")
                isShizukuPermissionGranted = false
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查 Shizuku 权限失败", e)
            isShizukuPermissionGranted = false
        }
    }

    /**
     * 初始化 Shizuku Binder 服务
     */
    private fun initializeShizukuBinder() {
        try {
            // 使用 bindUserService 创建系统级服务
            bindInstrumentationService()
            bindSystemAccessibilityService()

            // 通过 Shizuku 获取系统权限创建 Instrumentation
            instrumentation = Instrumentation()
            Log.d(TAG, "initializeShizukuBinder 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "初始化 initializeShizukuBinder 失败", e)
            instrumentation = null
            instrumentationService = null
            systemAccessibilityService = null
        }
    }

    /**
     * 绑定系统级Instrumentation服务
     */
    private fun bindInstrumentationService() {
        try {
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Shizuku权限未授予，无法绑定服务")
                return
            }

            val serviceArgs = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, InstrumentationService::class.java.name)
            )
            .daemon(false)
            .processNameSuffix("instrumentation")
            .debuggable(true)
            .version(1)

            userServiceArgs = serviceArgs

            val connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                    Log.d(TAG, "InstrumentationService 连接成功")
                    instrumentationService = IInstrumentationService.Stub.asInterface(binder)

                    // 测试服务是否可用
                    try {
                        val isAvailable = instrumentationService?.isServiceAvailable() ?: false
                        Log.d(TAG, "InstrumentationService 可用性: $isAvailable")
                    } catch (e: Exception) {
                        Log.e(TAG, "测试InstrumentationService失败", e)
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    Log.d(TAG, "InstrumentationService 连接断开")
                    instrumentationService = null
                }
            }

            serviceConnection = connection
            Shizuku.bindUserService(serviceArgs, connection)

        } catch (e: Exception) {
            Log.e(TAG, "绑定InstrumentationService失败", e)
            instrumentationService = null
        }
    }

    /**
     * 绑定系统级无障碍服务
     */
    private fun bindSystemAccessibilityService() {
        try {
            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Shizuku权限未授予，无法绑定无障碍服务")
                return
            }

            val serviceArgs = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, SystemAccessibilityService::class.java.name)
            )
            .daemon(false)
            .processNameSuffix("accessibility")
            .debuggable(true)
            .version(1)

            accessibilityUserServiceArgs = serviceArgs

            val connection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                    Log.d(TAG, "SystemAccessibilityService 连接成功")
                    systemAccessibilityService = IAccessibilityService.Stub.asInterface(binder)

                    // 测试服务是否可用
                    try {
                        val isAvailable = systemAccessibilityService?.isServiceAvailable() ?: false
                        Log.d(TAG, "SystemAccessibilityService 可用性: $isAvailable")
                    } catch (e: Exception) {
                        Log.e(TAG, "测试SystemAccessibilityService失败", e)
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    Log.d(TAG, "SystemAccessibilityService 连接断开")
                    systemAccessibilityService = null
                }
            }

            accessibilityServiceConnection = connection
            Shizuku.bindUserService(serviceArgs, connection)

        } catch (e: Exception) {
            Log.e(TAG, "绑定SystemAccessibilityService失败", e)
            systemAccessibilityService = null
        }
    }

    /**
     * 解绑系统级无障碍服务
     */
    fun unbindSystemAccessibilityService() {
        try {
            accessibilityServiceConnection?.let { connection ->
                accessibilityUserServiceArgs?.let { args ->
                    Shizuku.unbindUserService(args, connection, true)
                    Log.d(TAG, "SystemAccessibilityService 解绑成功")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解绑SystemAccessibilityService失败", e)
        } finally {
            systemAccessibilityService = null
            accessibilityServiceConnection = null
            accessibilityUserServiceArgs = null
        }
    }

    /**
     * 解绑Instrumentation服务
     */
    fun unbindInstrumentationService() {
        try {
            serviceConnection?.let { connection ->
                userServiceArgs?.let { args ->
                    Shizuku.unbindUserService(args, connection, true)
                    Log.d(TAG, "InstrumentationService 解绑成功")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "解绑InstrumentationService失败", e)
        } finally {
            instrumentationService = null
            serviceConnection = null
            userServiceArgs = null
        }
    }

    /**
     * 清理Shizuku资源
     */
    fun cleanupShizuku() {
        try {
            Log.d(TAG, "开始清理Shizuku资源")

            // 解绑服务
            unbindInstrumentationService()
            unbindSystemAccessibilityService()

            // 移除监听器
            try {
                Shizuku.removeRequestPermissionResultListener(shizukuPermissionResultListener)
                Shizuku.removeBinderReceivedListener(shizukuBinderReceivedListener)
                Shizuku.removeBinderDeadListener(shizukuBinderDeadListener)
            } catch (e: Exception) {
                Log.e(TAG, "移除Shizuku监听器失败", e)
            }

            // 重置状态
            instrumentation = null
            isShizukuAvailable = false
            isShizukuPermissionGranted = false

            Log.d(TAG, "Shizuku资源清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理Shizuku资源失败", e)
        }
    }

    /**
     * 强制清理Shizuku资源
     */
    fun forceCleanupShizuku() {
        try {
            Log.d(TAG, "开始强制清理Shizuku资源")
            cleanupShizuku()
        } catch (e: Exception) {
            Log.e(TAG, "强制清理Shizuku资源失败", e)
        }
    }

    // Getter methods for services
    fun getInstrumentationService(): IInstrumentationService? = instrumentationService
    fun getSystemAccessibilityService(): IAccessibilityService? = systemAccessibilityService
    fun getInstrumentation(): Instrumentation? = instrumentation
    fun isShizukuAvailable(): Boolean = isShizukuAvailable
    fun isShizukuPermissionGranted(): Boolean = isShizukuPermissionGranted
}