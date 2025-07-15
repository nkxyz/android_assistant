package com.xyz.xyzassister;

interface IAccessibilityService {
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