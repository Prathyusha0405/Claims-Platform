package com.chubb.claims.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The text must encode to at most {@link #value()} bytes in UTF-8. {@code @Size} counts characters, but bcrypt only
 * uses the first 72 <em>bytes</em> of a password, and a non-ASCII character takes 2-4 bytes. Without this check a long
 * password of such characters would be silently cut short, so two different passwords could behave as the same one.
 * {@code null} is valid; combine with {@code @NotBlank} to require a value.
 */
@Documented
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
@Target({METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE})
@Retention(RUNTIME)
public @interface MaxUtf8Bytes {

    int value();

    String message() default "must be at most {value} bytes when UTF-8 encoded; use fewer non-ASCII characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
