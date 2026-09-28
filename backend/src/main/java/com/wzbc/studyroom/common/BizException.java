package com.wzbc.studyroom.common;

/** 业务异常 */
public class BizException extends RuntimeException {
    private final int code;

    public BizException(String msg) {
        this(1001, msg);
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
