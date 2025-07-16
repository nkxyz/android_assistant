package com.xyz.xyzassister;

import com.xyz.xyzassister.IAccessibilityEventCallback;

interface IAccessibilityService {
    /**
     * 注册无障碍事件回调
     * @param callback 回调接口
     * @param eventTypes 要监听的事件类型（位掩码）
     */
    boolean registerEventCallback(IAccessibilityEventCallback callback, int eventTypes);

    /**
     * 取消注册无障碍事件回调
     * @param callback 回调接口
     */
    boolean unregisterEventCallback(IAccessibilityEventCallback callback);

    /**
     * 设置事件过滤器
     * @param packageNames 要监听的包名列表，null表示监听所有
     * @param eventTypes 要监听的事件类型
     */
    void setEventFilter(in String[] packageNames, int eventTypes);
    /**
     * 获取根节点信息
     */
    String getRootNodeInfo();

    /**
     * 根据ID查找节点
     */
    String findNodeById(String id);

    /**
     * 根据文本查找节点
     */
    String findNodeByText(String text);

    /**
     * 根据类名查找节点
     */
    String findNodeByClass(String className);

    /**
     * 点击指定节点
     */
    boolean clickNodeById(String id);

    /**
     * 点击指定文本的节点
     */
    boolean clickNodeByText(String text);

    /**
     * 获取当前活动窗口信息
     */
    String getCurrentWindowInfo();

    /**
     * 获取当前应用包名
     */
    String getCurrentPackageName();

    /**
     * 执行全局手势
     */
    boolean performGlobalAction(int action);

    /**
     * 检查服务是否可用
     */
    boolean isServiceAvailable();

    /**
     * 销毁服务
     */
    void destroy();
}
