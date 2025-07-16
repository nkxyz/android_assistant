package com.xyz.xyzassister

import android.app.Application
import android.util.Log
import rikka.shizuku.Shizuku

class XyzApplication : Application() {

    companion object {
        private const val TAG = "XyzApplication"
        private var instance: XyzApplication? = null

        fun getInstance(): XyzApplication? = instance
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "应用程序启动")
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "应用程序即将终止，开始清理Shizuku资源...")

        try {
            // 清理Shizuku用户服务
            cleanupShizukuServices()
            Log.i(TAG, "Shizuku资源清理完成")
        } catch (e: Exception) {
            Log.e(TAG, "清理Shizuku资源失败", e)
        }

        instance = null
        Log.i(TAG, "应用程序终止完成")
    }

    /**
     * 清理所有Shizuku相关资源
     */
    private fun cleanupShizukuServices() {
        try {
            // 获取服务实例并调用其清理方法
            val service = XyzService.getInstance()
            if (service != null) {
                Log.i(TAG, "通过XyzService清理Shizuku资源")
                // 调用服务的清理方法
                service.forceCleanupShizuku()
            } else {
                Log.w(TAG, "XyzService实例不存在，直接清理Shizuku资源")
                // 直接清理Shizuku资源
                directCleanupShizuku()
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理Shizuku服务异常", e)
            // 如果通过服务清理失败，尝试直接清理
            try {
                directCleanupShizuku()
            } catch (e2: Exception) {
                Log.e(TAG, "直接清理Shizuku资源也失败", e2)
            }
        }
    }

    /**
     * 直接清理Shizuku资源（不依赖无障碍服务）
     */
    private fun directCleanupShizuku() {
        try {
            // 移除所有可能的Shizuku监听器
            // 注意：这里我们不能访问具体的监听器实例，所以只能尝试通用清理
            Log.i(TAG, "执行直接Shizuku清理")

            // 这里可以添加更多的直接清理逻辑
            // 但由于Shizuku的用户服务是绑定到具体服务实例的，
            // 最好的方式还是通过无障碍服务来清理

        } catch (e: Exception) {
            Log.e(TAG, "直接清理Shizuku资源失败", e)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "系统内存不足")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "系统要求释放内存，级别: $level")
    }
}
