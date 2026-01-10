package io.github.samera2022.chinese_chess.rules;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记规则字段对应的常量名（RuleConstants 中的字符串常量）
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface RuleKey {
    String value();
}

