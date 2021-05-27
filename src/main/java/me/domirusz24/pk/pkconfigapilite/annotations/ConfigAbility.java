package me.domirusz24.pk.pkconfigapilite.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ConfigAbility {
    String value() default "ExtraAbilities";
    String configPath() default "config.yml";
    String languagePath() default "language.yml";
    String plugin() default "ProjectKorra";
}

