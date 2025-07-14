package com.xyz.xyzassister

import android.app.Instrumentation
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

class InstrumentationService : IInstrumentationService.Stub() {
    
    companion object {
        private const val TAG = "InstrumentationService"
    }
    
    private val instrumentation = Instrumentation()
    
    override fun click(x: Float, y: Float): Boolean {
        return try {
            val downTime = SystemClock.uptimeMillis()
            val eventTime = SystemClock.uptimeMillis()
            
            // 创建 ACTION_DOWN 事件
            val downEvent = MotionEvent.obtain(
                downTime, eventTime, MotionEvent.ACTION_DOWN, x, y, 0
            )
            
            // 创建 ACTION_UP 事件
            val upEvent = MotionEvent.obtain(
                downTime, eventTime + 100, MotionEvent.ACTION_UP, x, y, 0
            )
            
            // 发送事件
            instrumentation.sendPointerSync(downEvent)
            Thread.sleep(100)
            instrumentation.sendPointerSync(upEvent)
            
            // 回收事件
            downEvent.recycle()
            upEvent.recycle()
            
            Log.d(TAG, "系统级点击成功: ($x, $y)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级点击失败", e)
            false
        }
    }
    
    override fun longClick(x: Float, y: Float, duration: Long): Boolean {
        return try {
            val downTime = SystemClock.uptimeMillis()
            
            // 创建 ACTION_DOWN 事件
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0
            )
            instrumentation.sendPointerSync(downEvent)
            downEvent.recycle()
            
            // 等待指定时间
            Thread.sleep(duration)
            
            // 创建 ACTION_UP 事件
            val upEvent = MotionEvent.obtain(
                downTime, downTime + duration, MotionEvent.ACTION_UP, x, y, 0
            )
            instrumentation.sendPointerSync(upEvent)
            upEvent.recycle()
            
            Log.d(TAG, "系统级长按成功: ($x, $y), 持续时间: ${duration}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级长按失败", e)
            false
        }
    }
    
    override fun drag(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        return try {
            val downTime = SystemClock.uptimeMillis()
            val steps = (duration / 16).toInt().coerceAtLeast(1) // 16ms per step
            
            // 创建 ACTION_DOWN 事件
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0
            )
            instrumentation.sendPointerSync(downEvent)
            downEvent.recycle()
            
            // 创建中间的 ACTION_MOVE 事件
            for (i in 1 until steps) {
                val progress = i.toFloat() / steps
                val currentX = startX + (endX - startX) * progress
                val currentY = startY + (endY - startY) * progress
                val eventTime = downTime + (duration * progress).toLong()
                
                val moveEvent = MotionEvent.obtain(
                    downTime, eventTime, MotionEvent.ACTION_MOVE, currentX, currentY, 0
                )
                instrumentation.sendPointerSync(moveEvent)
                moveEvent.recycle()
                
                Thread.sleep(16) // 16ms delay for smooth animation
            }
            
            // 创建 ACTION_UP 事件
            val upEvent = MotionEvent.obtain(
                downTime, downTime + duration, MotionEvent.ACTION_UP, endX, endY, 0
            )
            instrumentation.sendPointerSync(upEvent)
            upEvent.recycle()
            
            Log.d(TAG, "系统级拖拽成功: ($startX, $startY) -> ($endX, $endY)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级拖拽失败", e)
            false
        }
    }
    
    override fun doubleClick(x: Float, y: Float): Boolean {
        return try {
            val firstClick = click(x, y)
            if (!firstClick) return false
            
            Thread.sleep(100) // 双击间隔
            
            val secondClick = click(x, y)
            if (!secondClick) return false
            
            Log.d(TAG, "系统级双击成功: ($x, $y)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级双击失败", e)
            false
        }
    }
    
    override fun slideSeekBar(startX: Float, startY: Float, endX: Float, endY: Float, steps: Int): Boolean {
        return try {
            val downTime = SystemClock.uptimeMillis()
            val actualSteps = steps.coerceAtLeast(10) // 至少10步确保平滑
            
            // 创建 ACTION_DOWN 事件
            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0
            )
            instrumentation.sendPointerSync(downEvent)
            downEvent.recycle()
            
            // 短暂停顿确保按下被识别
            Thread.sleep(50)
            
            // 创建平滑的滑动事件
            for (i in 1 until actualSteps) {
                val progress = i.toFloat() / actualSteps
                val currentX = startX + (endX - startX) * progress
                val currentY = startY + (endY - startY) * progress
                val eventTime = downTime + (i * 20L) // 每步20ms
                
                val moveEvent = MotionEvent.obtain(
                    downTime, eventTime, MotionEvent.ACTION_MOVE, currentX, currentY, 0
                )
                instrumentation.sendPointerSync(moveEvent)
                moveEvent.recycle()
                
                Thread.sleep(20) // 20ms delay for very smooth sliding
            }
            
            // 创建 ACTION_UP 事件
            val upEvent = MotionEvent.obtain(
                downTime, downTime + (actualSteps * 20L), MotionEvent.ACTION_UP, endX, endY, 0
            )
            instrumentation.sendPointerSync(upEvent)
            upEvent.recycle()
            
            Log.d(TAG, "系统级滑块操作成功: ($startX, $startY) -> ($endX, $endY), 步数: $actualSteps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级滑块操作失败", e)
            false
        }
    }
    
    override fun inputText(text: String): Boolean {
        return try {
            instrumentation.sendStringSync(text)
            Log.d(TAG, "系统级文本输入成功: $text")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级文本输入失败", e)
            false
        }
    }
    
    override fun sendKeyEvent(keyCode: Int): Boolean {
        return try {
            instrumentation.sendKeyDownUpSync(keyCode)
            Log.d(TAG, "系统级按键事件成功: $keyCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "系统级按键事件失败", e)
            false
        }
    }
    
    override fun isServiceAvailable(): Boolean {
        return try {
            // 简单测试Instrumentation是否可用
            instrumentation != null
        } catch (e: Exception) {
            false
        }
    }
    
    override fun destroy() {
        Log.d(TAG, "InstrumentationService 销毁")
        // 清理资源
    }
}