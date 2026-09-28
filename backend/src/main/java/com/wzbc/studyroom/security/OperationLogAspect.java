package com.wzbc.studyroom.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wzbc.studyroom.entity.OperationLog;
import com.wzbc.studyroom.mapper.OperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 操作日志切面：把「谁在什么时候改了什么」沉淀到 operation_log。
 *
 * <p>用 AOP 而不是在每个 Service 里手写一行记日志：前者不可能漏，
 * 后者迟早会因为「这个接口应该不用记吧」而出现缺口。
 *
 * <p>三条设计约束：
 * <ol>
 *   <li><b>只记写操作</b>。GET 不记——看板一进页面就打 7 个统计请求，
 *       全记下来日志表会被读操作淹没，真正的改动反而找不到。</li>
 *   <li><b>参数必须脱敏</b>。登录/注册/改密的请求体里带着明文口令，
 *       原样落库等于把口令以明文形式又存了一份，比不加日志更糟。</li>
 *   <li><b>记日志失败绝不向上抛</b>。日志是旁路，不能因为日志表写不进去
 *       就让用户的预约失败。整个写入过程包在 try/catch 里。</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    /** 口令与密钥类字段一律替换为 ***，绝不落库 */
    private static final Pattern SECRET = Pattern.compile(
            "(?i)\"(password|newPassword|oldPassword|confirmPassword|apiKey|token|secret|code)\"\\s*:\\s*\"[^\"]*\"");

    private static final int MAX_PARAMS = 1000;

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "DELETE", "PATCH");

    @Around("execution(public * com.wzbc.studyroom.controller..*.*(..))")
    public Object record(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest req = currentRequest();
        if (req == null || !WRITE_METHODS.contains(req.getMethod())) {
            return pjp.proceed();
        }

        long startedAt = System.currentTimeMillis();
        boolean success = true;
        try {
            return pjp.proceed();
        } catch (Throwable e) {
            success = false;
            throw e;
        } finally {
            try {
                // 此刻业务事务已经提交或回滚完毕，这条插入走自己的连接，
                // 不会因为业务回滚而一起消失（失败的操作同样需要留痕）
                save(req, pjp, success, (int) (System.currentTimeMillis() - startedAt));
            } catch (Exception e) {
                log.warn("操作日志写入失败，已忽略：{}", e.getMessage());
            }
        }
    }

    private void save(HttpServletRequest req, ProceedingJoinPoint pjp, boolean success, int costMs) {
        OperationLog entry = new OperationLog();

        LoginUser me = UserContext.get();
        if (me != null) {
            entry.setUserId(me.getId());
            entry.setUsername(me.getPhone());
        }

        String controller = pjp.getSignature().getDeclaringType().getSimpleName();
        entry.setModule(controller.endsWith("Controller")
                ? controller.substring(0, controller.length() - "Controller".length())
                : controller);
        entry.setAction(pjp.getSignature().getName());
        entry.setMethod(req.getMethod() + " " + req.getRequestURI());
        entry.setParams(describeArgs(pjp));
        entry.setIp(clientIp(req));
        entry.setSuccess(success ? 1 : 0);
        entry.setCostMs(costMs);
        entry.setCreatedAt(LocalDateTime.now());

        operationLogMapper.insert(entry);
    }

    /** 把方法入参序列化成一行可读文本，并做脱敏与截断 */
    private String describeArgs(ProceedingJoinPoint pjp) {
        try {
            Object[] args = Arrays.stream(pjp.getArgs())
                    .filter(a -> !(a instanceof HttpServletRequest)
                            && !(a instanceof HttpServletResponse)
                            && !(a instanceof MultipartFile))
                    .toArray();
            if (args.length == 0) {
                return null;
            }
            String json = objectMapper.writeValueAsString(args);
            json = SECRET.matcher(json).replaceAll("\"$1\":\"***\"");
            if (json.length() > MAX_PARAMS) {
                json = json.substring(0, MAX_PARAMS) + "…[已截断]";
            }
            return json;
        } catch (Exception e) {
            // 序列化失败（如参数里有不可序列化的对象）就放弃记参数，不影响业务
            return null;
        }
    }

    private String clientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
