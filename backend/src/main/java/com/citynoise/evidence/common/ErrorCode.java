package com.citynoise.evidence.common;

import lombok.Getter;

/**
 * 业务错误码。
 */
@Getter
public enum ErrorCode {

    PARAM_INVALID(40000, "参数校验失败"),
    UNAUTHORIZED(40100, "未登录或登录已过期"),
    FORBIDDEN(40300, "无权限访问"),
    NOT_FOUND(40400, "资源不存在"),

    DUPLICATE_RECORD(40901, "重复提交：该传感器在相同采样时间与相同数据哈希下已存在记录"),
    BATCH_OVERLAP(40902, "采样批次时间冲突：同一传感器存在时间区间重叠的采样批次"),
    CONCURRENT_CONFLICT(40903, "并发冲突：记录正在被其他请求处理，请重试"),
    DUPLICATE_ANOMALY(40904, "该记录已存在相同类型的异常事件"),
    BATCH_CLOSED(40905, "采样批次已关闭，禁止写入新记录"),

    IMPORT_ALL_FAILED(42201, "全部记录导入失败"),
    SENSOR_NOT_FOUND(40401, "传感器不存在"),
    RECORD_NOT_FOUND(40402, "噪声记录不存在"),
    ANOMALY_NOT_FOUND(40403, "异常事件不存在"),

    INTERNAL_ERROR(50000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
