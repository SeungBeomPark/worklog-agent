package com.example.worklogagent.repository;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * worklog.storage 설정값에 따라 저장소 구현체를 하나만 활성화한다.
 *   worklog.storage=excel → ExcelWorkLogRepository
 *   worklog.storage=db    → JpaWorkLogRepository
 * 설정이 없으면 기본은 excel.
 */
public class StorageCondition {

    private static String storage(ConditionContext ctx) {
        String v = ctx.getEnvironment().getProperty("worklog.storage");
        return (v == null || v.isBlank()) ? "excel" : v.trim().toLowerCase();
    }

    /** worklog.storage=excel 일 때 매칭 */
    public static class Excel implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata meta) {
            return "excel".equals(storage(ctx));
        }
    }

    /** worklog.storage=db 일 때 매칭 */
    public static class Db implements Condition {
        @Override
        public boolean matches(ConditionContext ctx, AnnotatedTypeMetadata meta) {
            return "db".equals(storage(ctx));
        }
    }
}
