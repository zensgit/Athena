package com.ecm.core.scheduler;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;

import java.lang.reflect.Method;

/**
 * Stable, proxy-safe job-id derivation (§3A.3). The same scheme is used by the aspect (run data)
 * and the observability service (schedule / next-run via ScheduledTaskHolder) so the two JOIN on
 * one id.
 *
 * <p>Form: {@code declaringClass#methodName}, taken from the UNWRAPPED target class (never a
 * CGLIB / {@code $$SpringCGLIB$$} proxy name). Overloaded / same-named methods get a deterministic
 * parameter-type suffix so they remain distinct.
 */
public final class SchedulerJobIds {

    private SchedulerJobIds() {
    }

    public static String of(Class<?> targetClass, Method method) {
        // forJoinPoint / the service already unwrap via AopUtils.getTargetClass; as belt-and-suspenders
        // strip any residual Spring CGLIB marker (e.g. Foo$$SpringCGLIB$$0) to the real class FQN.
        String className = targetClass.getName();
        int cglibMarker = className.indexOf("$$");
        if (cglibMarker > 0) {
            className = className.substring(0, cglibMarker);
        }
        String base = className + "#" + method.getName();
        if (method.getParameterCount() == 0) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params[i].getSimpleName());
        }
        return sb.append(')').toString();
    }

    public static String forJoinPoint(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget() != null
            ? AopUtils.getTargetClass(joinPoint.getTarget())
            : signature.getDeclaringType();
        return of(targetClass, signature.getMethod());
    }
}
