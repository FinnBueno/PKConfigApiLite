package me.domirusz24.pk.pkconfigapilite.annotations;

import com.projectkorra.projectkorra.configuration.ConfigType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigValue  {
    String value() default "";
    String type() default "config";
}
