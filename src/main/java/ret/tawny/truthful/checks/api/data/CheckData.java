package ret.tawny.truthful.checks.api.data;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME)
@Target(TYPE)
public @interface CheckData {
    char order();

    CheckType type();

    /**
     * Optional display name override for the check type prefix.
     * If empty (default), uses the CheckType's name.
     * Used by bedrock checks to show names such as "B Speed A" instead of "Bedrock(A)".
     */
    String displayName() default "";
}
