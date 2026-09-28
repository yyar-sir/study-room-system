package com.wzbc.studyroom.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p><b>HTTP 状态码约定</b>（这里刻意区分，不要图省事全返回 200）：
 * <ul>
 *   <li>{@code 401 / 403 / 404 / 405} —— 对应真实的 HTTP 状态码。理由：越权若返回
 *       HTTP 200 + body.code=403，Postman、接口回归脚本等按 HTTP 语义判断的工具
 *       会把它当成"请求成功"，越权检测直接失效。</li>
 *   <li>业务失败（如"座位已被预约"）—— HTTP 200 + {@code code != 0}。
 *       这类是<b>正常业务流</b>而非协议层错误，用 body 里的业务码表达。</li>
 * </ul>
 *
 * <p>无论哪种，响应体始终是统一的 {@code {code, msg, data}}，前端只需认这一套结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status = switch (e.getCode()) {
            case ErrorCode.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case ErrorCode.FORBIDDEN -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Result.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 参数校验失败。
     * 刻意返回 HTTP 200 而非 400：前端拦截器对非 401/403 的 HTTP 错误会笼统提示
     * 「网络异常，请稍后再试」，那会把「手机号格式不正确」这种明确提示盖掉。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getDefaultMessage() : "参数校验失败";
        return Result.fail(ErrorCode.PARAM_INVALID, msg);
    }

    /**
     * 路由不存在 → 明确告知，不要被下面的兜底吞成「系统繁忙」。
     * 之前缺这一个分支，导致调错路径时返回 9999，排查时极易误判成服务故障。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(404, "接口不存在：" + e.getResourcePath()));
    }

    /** 请求方法不匹配（例如把 POST 接口当 GET 调） */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.fail(405, "请求方法不支持：" + e.getMethod()));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(9999, "系统繁忙，请稍后再试");
    }
}
