package br.com.fiap.solovivo.dto;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/** Protecao contra XSS: rejeita textos com os caracteres &lt; ou &gt; (tags HTML/script). */
@Documented
@Constraint(validatedBy = SemHtmlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SemHtml {

    String message() default "não pode conter HTML (caracteres < ou >)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
