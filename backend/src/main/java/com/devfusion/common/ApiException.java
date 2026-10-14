package com.devfusion.common;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final int code;

    public ApiException(String msg) {
        this(400, msg);
    }

    public ApiException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public static ApiException notFound(String what) {
        return new ApiException(404, what + "不存在");
    }

    public static ApiException forbidden() {
        return new ApiException(403, "无权限操作");
    }
}
