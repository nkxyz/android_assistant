package com.xyz.xyzassister;

interface IInstrumentationService {
    /**
     * 模拟点击
     */
    boolean click(float x, float y);
    
    /**
     * 模拟长按
     */
    boolean longClick(float x, float y, long duration);
    
    /**
     * 模拟拖拽/滑动
     */
    boolean drag(float startX, float startY, float endX, float endY, long duration);
    
    /**
     * 模拟双击
     */
    boolean doubleClick(float x, float y);
    
    /**
     * 模拟滑块操作
     */
    boolean slideSeekBar(float startX, float startY, float endX, float endY, int steps);
    
    /**
     * 模拟输入文本
     */
    boolean inputText(String text);
    
    /**
     * 模拟按键事件
     */
    boolean sendKeyEvent(int keyCode);
    
    /**
     * 检查服务是否可用
     */
    boolean isServiceAvailable();
    
    /**
     * 销毁服务
     */
    void destroy();
}