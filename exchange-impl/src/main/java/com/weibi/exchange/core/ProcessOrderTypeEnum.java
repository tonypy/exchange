package com.weibi.exchange.core;

public enum ProcessOrderTypeEnum {
    CREATE_ORDER("CREATE_ORDER", "创建订单"),
    CANCEL_ORDER("CANCEL_ORDER", "取消订单"),
    ACTIVE_STOP_LIMIT_ORDER("ACTIVE_STOP_LIMIT_ORDER", "激活委托订单");
    private final String code;
    private final String desc;

    ProcessOrderTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ProcessOrderTypeEnum getByCode(String code) {
        for (ProcessOrderTypeEnum each : values()) {
            if (code != null && code.equalsIgnoreCase(each.getCode())) {
                return each;
            }
        }
        return null;
    }
}