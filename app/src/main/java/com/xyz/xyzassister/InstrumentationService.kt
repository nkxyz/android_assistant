package com.xyz.xyzassister

import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import kotlin.math.abs

class InstrumentationService : IInstrumentationService.Stub() {

    companion object {
        private const val TAG = "InputManagerService"
        private const val INPUT_SERVICE = "input"
        private const val INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT = 2
    }

    private var inputManagerService: Any? = null
    private var injectInputEventMethod: java.lang.reflect.Method? = null

    init {
        initializeInputManager()
    }

    /**
     * 初始化InputManager服务和反射方法
     */
    private fun initializeInputManager() {
        try {
            // 获取ServiceManager类
            val serviceManagerClass = Class.forName("android.os.ServiceManager")
            val getServiceMethod = serviceManagerClass.getMethod("getService", String::class.java)

            // 获取InputManager服务
            val inputManagerBinder = getServiceMethod.invoke(null, INPUT_SERVICE)

            // 获取InputManagerService的Stub类
            val inputManagerStubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterfaceMethod = inputManagerStubClass.getMethod("asInterface", android.os.IBinder::class.java)

            // 创建InputManagerService实例
            inputManagerService = asInterfaceMethod.invoke(null, inputManagerBinder)

            // 获取injectInputEvent方法
            val inputManagerClass = Class.forName("android.hardware.input.IInputManager")
            injectInputEventMethod = inputManagerClass.getMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )

            Log.i(TAG, "InputManager服务初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "InputManager服务初始化失败", e)
            inputManagerService = null
            injectInputEventMethod = null
        }
    }

    /**
     * 注入输入事件
     */
    private fun injectInputEvent(event: InputEvent): Boolean {
        return try {
            val service = inputManagerService ?: return false
            val method = injectInputEventMethod ?: return false

            val result = method.invoke(service, event, INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT) as Boolean
            result
        } catch (e: Exception) {
            Log.e(TAG, "注入输入事件失败", e)
            false
        }
    }

    override fun click(x: Float, y: Float): Boolean {
        Log.i(TAG, "[Binder调用] click() 开始执行 - 坐标: ($x, $y)")
        val startTime = SystemClock.uptimeMillis()

        return try {
            val downTime = SystemClock.uptimeMillis()
            // 创建 ACTION_DOWN 事件 - 匹配真实点击参数
            val downEvent = MotionEvent.obtain(
                downTime,                           // downTime
                downTime,                           // eventTime
                MotionEvent.ACTION_DOWN,            // action
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )

            val downSuccess = injectInputEvent(downEvent)

            // 模拟真实点击的时间间隔 (约289ms根据日志)
            Thread.sleep(89)

            // 创建 ACTION_UP 事件 - 匹配真实点击参数
            val upEventTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime,                           // downTime
                upEventTime,                           // eventTime
                MotionEvent.ACTION_UP,            // action
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )

            // 设置输入源为触摸屏，但实际日志显示source=0x0，所以不设置source
            // upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN)

            val upSuccess = injectInputEvent(upEvent)

            // 回收事件
            downEvent.recycle()
            upEvent.recycle()

            val success = downSuccess && upSuccess
            val duration = SystemClock.uptimeMillis() - startTime
            if (success) {
                Log.i(TAG, "[Binder调用] click() 执行成功 - 坐标: ($x, $y), 耗时: ${duration}ms")
            } else {
                Log.e(TAG, "[Binder调用] click() 执行失败 - 坐标: ($x, $y), 耗时: ${duration}ms")
            }
            success
        } catch (e: Exception) {
            val duration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] click() 执行失败 - 坐标: ($x, $y), 耗时: ${duration}ms", e)
            false
        }
    }

    override fun longClick(x: Float, y: Float, duration: Long): Boolean {
        Log.i(TAG, "[Binder调用] longClick() 开始执行 - 坐标: ($x, $y), 持续时间: ${duration}ms")
        val startTime = SystemClock.uptimeMillis()

        return try {
            // 创建 ACTION_DOWN 事件 - 匹配真实点击参数
            val downTime = SystemClock.uptimeMillis()
            val downEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )

            val downSuccess = injectInputEvent(downEvent)

            // 模拟真实点击的时间间隔 (约289ms根据日志)
            Thread.sleep(duration)

            // 创建 ACTION_UP 事件 - 匹配真实点击参数
            val upEventTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_UP,
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )

            val upSuccess = injectInputEvent(upEvent)
            upEvent.recycle()

            val success = upSuccess && downSuccess
            val totalDuration = SystemClock.uptimeMillis() - startTime
            if (success) {
                Log.i(TAG, "[Binder调用] longClick() 执行成功 - 坐标: ($x, $y), 长按时间: ${duration}ms, 总耗时: ${totalDuration}ms")
            } else {
                Log.e(TAG, "[Binder调用] longClick() 执行失败 - 坐标: ($x, $y), 长按时间: ${duration}ms, 总耗时: ${totalDuration}ms")
            }
            success
        } catch (e: Exception) {
            val totalDuration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] longClick() 执行失败 - 坐标: ($x, $y), 长按时间: ${duration}ms, 总耗时: ${totalDuration}ms", e)
            false
        }
    }

    override fun drag(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        val distance = kotlin.math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
        Log.i(TAG, "[Binder调用] drag() 开始执行 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 持续时间: ${duration}ms")
        val startTime = SystemClock.uptimeMillis()

        return try {
            val downTime = SystemClock.uptimeMillis()
            val steps = (duration / 16).toInt().coerceAtLeast(1) // 16ms per step for smooth animation

            Log.i(TAG, "[Binder调用] drag() 计算步数: $steps 步")

            val source = InputDevice.SOURCE_TOUCHSCREEN

            // 替换所有 MotionEvent.obtain 调用
            val downEvent = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = startX
                    this.y = startY
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, source,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )
            val downSuccess = injectInputEvent(downEvent)
            downEvent.recycle()

            if (!downSuccess) {
                Log.e(TAG, "[Binder调用] drag() ACTION_DOWN 事件注入失败")
                return false
            }

            // 创建中间的 ACTION_MOVE 事件 - 匹配真实点击参数
            for (i in 1 until steps) {
                val progress = i.toFloat() / steps
                val currentX = startX + (endX - startX) * progress
                val currentY = startY + (endY - startY) * progress

                val moveTime = SystemClock.uptimeMillis()
                val moveEvent = MotionEvent.obtain(
                    downTime,
                    moveTime,
                    MotionEvent.ACTION_MOVE,
                    1,
                    arrayOf(MotionEvent.PointerProperties().apply {
                        id = 0
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }),
                    arrayOf(MotionEvent.PointerCoords().apply {
                        this.x = currentX
                        this.y = currentY
                        pressure = 1.0f
                        size = 1.0f
                    }),
                    0, 0, 1.0f, 1.0f,
                    -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
                )
                val moveSuccess = injectInputEvent(moveEvent)
                moveEvent.recycle()

                if (!moveSuccess) {
                    Log.w(TAG, "[Binder调用] drag() ACTION_MOVE 事件注入失败，步骤: $i")
                }

                Thread.sleep(16) // 16ms delay for smooth animation
            }

            // 创建 ACTION_UP 事件 - 匹配真实点击参数
            Thread.sleep(duration)
            val upEventTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime,
                upEventTime,
                MotionEvent.ACTION_UP,
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    this.x = endX
                    this.y = endY
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )
            val upSuccess = injectInputEvent(upEvent)
            upEvent.recycle()

            val success = upSuccess
            val totalDuration = SystemClock.uptimeMillis() - startTime
            if (success) {
                Log.i(TAG, "[Binder调用] drag() 执行成功 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 步数: $steps, 总耗时: ${totalDuration}ms")
            } else {
                Log.e(TAG, "[Binder调用] drag() 执行失败 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 总耗时: ${totalDuration}ms")
            }
            success
        } catch (e: Exception) {
            val totalDuration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] drag() 执行失败 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 总耗时: ${totalDuration}ms", e)
            false
        }
    }

    override fun doubleClick(x: Float, y: Float): Boolean {
        Log.d(TAG, "[Binder调用] doubleClick() 开始执行 - 坐标: ($x, $y)")
        val startTime = SystemClock.uptimeMillis()

        return try {
            Log.d(TAG, "[Binder调用] doubleClick() 执行第一次点击")
            val firstClick = click(x, y)
            if (!firstClick) {
                Log.w(TAG, "[Binder调用] doubleClick() 第一次点击失败")
                return false
            }

            Thread.sleep(100) // 双击间隔

            Log.d(TAG, "[Binder调用] doubleClick() 执行第二次点击")
            val secondClick = click(x, y)
            if (!secondClick) {
                Log.w(TAG, "[Binder调用] doubleClick() 第二次点击失败")
                return false
            }

            val totalDuration = SystemClock.uptimeMillis() - startTime
            Log.d(TAG, "[Binder调用] doubleClick() 执行成功 - 坐标: ($x, $y), 总耗时: ${totalDuration}ms")
            true
        } catch (e: Exception) {
            val totalDuration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] doubleClick() 执行失败 - 坐标: ($x, $y), 总耗时: ${totalDuration}ms", e)
            false
        }
    }

    override fun slideSeekBar(startX: Float, startY: Float, endX: Float, endY: Float, steps: Int): Boolean {
        val distance = kotlin.math.sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
        Log.d(TAG, "[Binder调用] slideSeekBar() 开始执行 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 请求步数: $steps")
        val startTime = SystemClock.uptimeMillis()

        return try {
            val downTime = SystemClock.uptimeMillis()
            val actualSteps = steps.coerceAtLeast(10) // 至少10步确保平滑

            Log.d(TAG, "[Binder调用] slideSeekBar() 实际步数: $actualSteps (最小10步)")

            // 创建 ACTION_DOWN 事件 - 匹配真实点击参数
            val downEvent = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_DOWN,
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    x = endX
                    y = endY
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )
            val downSuccess = injectInputEvent(downEvent)
            downEvent.recycle()

            if (!downSuccess) {
                Log.e(TAG, "[Binder调用] slideSeekBar() ACTION_DOWN 事件注入失败")
                return false
            }

            // 短暂停顿确保按下被识别
            Thread.sleep(50)

            val RANDOM_X_RANGE = 0.1f  // X轴随机量范围（屏幕宽度的10%）
            val RANDOM_Y_RANGE = 0.05f // Y轴随机量范围（屏幕高度的5%）

            // 创建平滑的滑动事件 - 匹配真实点击参数
            for (i in 1 until actualSteps) {


                val baseProgress = i.toFloat() / actualSteps
                // 生成随机因子（-0.1到0.1之间）
                val randomFactor = (Math.random() * 0.2 - 0.1).toFloat()

                // 带随机量的进度计算（最后一步保持准确）
                val effectiveProgress = if (i == actualSteps - 1) 1.0f else
                    baseProgress + randomFactor * 0.3f  // 限制随机幅度

                // X轴基础位移 + 随机波动
                val currentX = startX + (endX - startX) * effectiveProgress +
                        (endX - startX) * (Math.random() * RANDOM_X_RANGE - RANDOM_X_RANGE/2).toFloat()

                // Y轴基础位移 + 随机波动（模拟手指自然抖动）
                val yOffset = (endY - startY) * 0.2f // 允许20%的垂直偏移
                val currentY = startY + (endY - startY) * effectiveProgress +
                        yOffset * (Math.random() * RANDOM_Y_RANGE - RANDOM_Y_RANGE/2).toFloat()

                // 限制坐标不超出边界
                val clampedX = currentX.coerceIn(startX.coerceAtMost(endX), startX.coerceAtLeast(endX))
                val clampedY = currentY.coerceIn(startY.coerceAtMost(endY), startY.coerceAtLeast(endY))


//                val progress = i.toFloat() / actualSteps
//                val currentX = startX + (endX - startX) * progress
//                val currentY = startY + (endY - startY) * progress

                val moveEvent = MotionEvent.obtain(
                    downTime,
                    SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE,
                    1,
                    arrayOf(MotionEvent.PointerProperties().apply {
                        id = 0
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }),
                    arrayOf(MotionEvent.PointerCoords().apply {
                        x = clampedX
                        y = clampedY
                        pressure = 1.0f
                        size = 1.0f
                    }),
                    0, 0, 1.0f, 1.0f,
                    -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
                )
                val moveSuccess = injectInputEvent(moveEvent)
                moveEvent.recycle()

                if (!moveSuccess) {
                    Log.w(TAG, "[Binder调用] slideSeekBar() ACTION_MOVE 事件注入失败，步骤: $i")
                }

                Thread.sleep(20) // 20ms delay for very smooth sliding
            }

            // 创建 ACTION_UP 事件 - 匹配真实点击参数
            val upEvent = MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP,
                1,
                arrayOf(MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }),
                arrayOf(MotionEvent.PointerCoords().apply {
                    x = endX
                    y = endY
                    pressure = 1.0f
                    size = 1.0f
                }),
                0, 0, 1.0f, 1.0f,
                -1, 0, InputDevice.SOURCE_TOUCHSCREEN,
                MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
            )
            val upSuccess = injectInputEvent(upEvent)
            upEvent.recycle()

            val success = upSuccess
            val totalDuration = SystemClock.uptimeMillis() - startTime
            if (success) {
                Log.d(TAG, "[Binder调用] slideSeekBar() 执行成功 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 实际步数: $actualSteps, 总耗时: ${totalDuration}ms")
            } else {
                Log.e(TAG, "[Binder调用] slideSeekBar() 执行失败 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 总耗时: ${totalDuration}ms")
            }
            success
        } catch (e: Exception) {
            val totalDuration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] slideSeekBar() 执行失败 - 起点: ($startX, $startY), 终点: ($endX, $endY), 距离: ${distance.toInt()}px, 总耗时: ${totalDuration}ms", e)
            false
        }
    }

    override fun inputText(text: String): Boolean {
        Log.d(TAG, "[Binder调用] inputText() 开始执行 - 文本长度: ${text.length}, 内容: \"$text\"")
        val startTime = SystemClock.uptimeMillis()

        return try {
            var allSuccess = true
            val downTime = SystemClock.uptimeMillis()

            // 将文本转换为字符数组并逐个发送
            for (char in text) {
                // 获取字符对应的键码
                val keyCode = getKeyCodeForChar(char)
                if (keyCode != KeyEvent.KEYCODE_UNKNOWN) {
                    // 创建按键按下事件
                    val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
                    val downSuccess = injectInputEvent(downEvent)

                    // 创建按键释放事件
                    val upEvent = KeyEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode, 0)
                    val upSuccess = injectInputEvent(upEvent)

                    if (!downSuccess || !upSuccess) {
                        Log.w(TAG, "[Binder调用] inputText() 字符 '$char' (keyCode: $keyCode) 注入失败")
                        allSuccess = false
                    }
                } else {
                    Log.w(TAG, "[Binder调用] inputText() 无法为字符 '$char' 找到对应的键码")
                    allSuccess = false
                }

                // 短暂延迟避免输入过快
                Thread.sleep(50)
            }

            val duration = SystemClock.uptimeMillis() - startTime
            if (allSuccess) {
                Log.d(TAG, "[Binder调用] inputText() 执行成功 - 文本长度: ${text.length}, 内容: \"$text\", 耗时: ${duration}ms")
            } else {
                Log.e(TAG, "[Binder调用] inputText() 部分失败 - 文本长度: ${text.length}, 内容: \"$text\", 耗时: ${duration}ms")
            }
            allSuccess
        } catch (e: Exception) {
            val duration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] inputText() 执行失败 - 文本长度: ${text.length}, 内容: \"$text\", 耗时: ${duration}ms", e)
            false
        }
    }

    /**
     * 获取字符对应的键码
     */
    private fun getKeyCodeForChar(char: Char): Int {
        return when (char) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (char - 'a')
            in 'A'..'Z' -> KeyEvent.KEYCODE_A + (char - 'A')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (char - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            '.' -> KeyEvent.KEYCODE_PERIOD
            ',' -> KeyEvent.KEYCODE_COMMA
            '?' -> KeyEvent.KEYCODE_SLASH // 需要配合Shift
            '!' -> KeyEvent.KEYCODE_1 // 需要配合Shift
            '@' -> KeyEvent.KEYCODE_2 // 需要配合Shift
            '#' -> KeyEvent.KEYCODE_3 // 需要配合Shift
            '$' -> KeyEvent.KEYCODE_4 // 需要配合Shift
            '%' -> KeyEvent.KEYCODE_5 // 需要配合Shift
            '^' -> KeyEvent.KEYCODE_6 // 需要配合Shift
            '&' -> KeyEvent.KEYCODE_7 // 需要配合Shift
            '*' -> KeyEvent.KEYCODE_8 // 需要配合Shift
            '(' -> KeyEvent.KEYCODE_9 // 需要配合Shift
            ')' -> KeyEvent.KEYCODE_0 // 需要配合Shift
            '-' -> KeyEvent.KEYCODE_MINUS
            '=' -> KeyEvent.KEYCODE_EQUALS
            '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '\\' -> KeyEvent.KEYCODE_BACKSLASH
            ';' -> KeyEvent.KEYCODE_SEMICOLON
            '\'' -> KeyEvent.KEYCODE_APOSTROPHE
            '/' -> KeyEvent.KEYCODE_SLASH
            '\n' -> KeyEvent.KEYCODE_ENTER
            '\t' -> KeyEvent.KEYCODE_TAB
            else -> KeyEvent.KEYCODE_UNKNOWN
        }
    }

    override fun sendKeyEvent(keyCode: Int): Boolean {
        val keyName = KeyEvent.keyCodeToString(keyCode)
        Log.d(TAG, "[Binder调用] sendKeyEvent() 开始执行 - 按键码: $keyCode ($keyName)")
        val startTime = SystemClock.uptimeMillis()

        return try {
            val downTime = SystemClock.uptimeMillis()

            // 创建按键按下事件
            val downEvent = KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode, 0)
            val downSuccess = injectInputEvent(downEvent)

            // 创建按键释放事件
            val upEvent = KeyEvent(downTime, downTime + 100, KeyEvent.ACTION_UP, keyCode, 0)
            val upSuccess = injectInputEvent(upEvent)

            val success = downSuccess && upSuccess
            val duration = SystemClock.uptimeMillis() - startTime
            if (success) {
                Log.d(TAG, "[Binder调用] sendKeyEvent() 执行成功 - 按键码: $keyCode ($keyName), 耗时: ${duration}ms")
            } else {
                Log.e(TAG, "[Binder调用] sendKeyEvent() 执行失败 - 按键码: $keyCode ($keyName), 耗时: ${duration}ms")
            }
            success
        } catch (e: Exception) {
            val duration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] sendKeyEvent() 执行失败 - 按键码: $keyCode ($keyName), 耗时: ${duration}ms", e)
            false
        }
    }

    override fun isServiceAvailable(): Boolean {
        Log.d(TAG, "[Binder调用] isServiceAvailable() 开始执行 - 检查服务可用性")
        val startTime = SystemClock.uptimeMillis()

        return try {
            // 测试InputManager服务是否可用
            val isAvailable = inputManagerService != null && injectInputEventMethod != null

            val duration = SystemClock.uptimeMillis() - startTime
            Log.d(TAG, "[Binder调用] isServiceAvailable() 执行完成 - 结果: $isAvailable, 耗时: ${duration}ms")
            isAvailable
        } catch (e: Exception) {
            val duration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] isServiceAvailable() 执行异常 - 返回false, 耗时: ${duration}ms", e)
            false
        }
    }

    override fun destroy() {
        Log.d(TAG, "[Binder调用] destroy() 开始执行 - 销毁InstrumentationService")
        val startTime = SystemClock.uptimeMillis()

        try {
            // 清理资源
            // 这里可以添加具体的资源清理逻辑

            val duration = SystemClock.uptimeMillis() - startTime
            Log.d(TAG, "[Binder调用] destroy() 执行完成 - InstrumentationService已销毁, 耗时: ${duration}ms")
        } catch (e: Exception) {
            val duration = SystemClock.uptimeMillis() - startTime
            Log.e(TAG, "[Binder调用] destroy() 执行异常 - 销毁过程中出现错误, 耗时: ${duration}ms", e)
        }
    }
}
