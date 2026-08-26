package com.etake.cyclicaction.config.annotations;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(value = {
        ElementType.FIELD,
        ElementType.METHOD,
        ElementType.PARAMETER,
        ElementType.TYPE,
        ElementType.ANNOTATION_TYPE
})
@Retention(value = RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Qualifier
public @interface ActualAvgSalesReader {
    @AliasFor(annotation = Qualifier.class)
    String value() default "";
}
