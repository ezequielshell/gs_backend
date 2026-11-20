package br.com.fiap.solovivo.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class SemHtmlValidator implements ConstraintValidator<SemHtml, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {
        return valor == null || (valor.indexOf('<') < 0 && valor.indexOf('>') < 0);
    }
}
