package bo.edu.uagrm.tienda.dto.validacion;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MaximoBytesUtf8Validator implements ConstraintValidator<MaximoBytesUtf8, String> {

	private int maximo;

	@Override
	public void initialize(MaximoBytesUtf8 anotacion) {
		maximo = anotacion.value();
	}

	@Override
	public boolean isValid(String valor, ConstraintValidatorContext contexto) {
		return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= maximo;
	}
}
