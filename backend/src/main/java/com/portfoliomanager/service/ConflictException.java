package com.portfoliomanager.service;

/**
 * 业务冲突异常，由 ApiExceptionHandler 统一转换为 HTTP 409 Conflict。
 * message 字段即为返回给客户端的 error code，例如：
 *   PORTFOLIO_NAME_CONFLICT  — 活跃组合名称重复
 *   PORTFOLIO_HAS_TRADES     — 组合有交易历史，不能硬删除
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String code) {
        super(code);
    }
}
