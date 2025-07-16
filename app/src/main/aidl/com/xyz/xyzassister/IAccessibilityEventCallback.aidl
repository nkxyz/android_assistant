package com.xyz.xyzassister;

interface IAccessibilityEventCallback {
    /**
     * 接收无障碍事件回调
     * @param eventType 事件类型
     * @param packageName 包名
     * @param className 类名
     * @param text 文本内容
     * @param contentDescription 内容描述
     * @param eventTime 事件时间
     * @param windowId 窗口ID
     */
    void onAccessibilityEvent(
        int eventType,
        String packageName,
        String className,
        String text,
        String contentDescription,
        long eventTime,
        int windowId
    );
    
    /**
     * 服务中断回调
     */
    void onInterrupt();
    
    /**
     * 服务连接状态变化回调
     * @param connected 是否已连接
     */
    void onServiceConnectionChanged(boolean connected);
}