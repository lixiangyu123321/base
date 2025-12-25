package com.lixiangyu.common.constant;

/**
 * 通用常量类
 *
 * @author lixiangyu
 */
public class CommonConstants {

    /**
     * 成功状态码
     */
    public static final int SUCCESS_CODE = 200;

    /**
     * 失败状态码
     */
    public static final int FAIL_CODE = 500;

    /**
     * 成功消息
     */
    public static final String SUCCESS_MSG = "操作成功";

    /**
     * 失败消息
     */
    public static final String FAIL_MSG = "操作失败";

    /**
     * 默认分页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE_NUM = 1;

    private CommonConstants() {
        // 工具类，禁止实例化
    }
}

