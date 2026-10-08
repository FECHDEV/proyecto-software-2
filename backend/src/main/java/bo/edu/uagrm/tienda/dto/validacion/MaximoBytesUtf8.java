package bo.edu.uagrm.tienda.dto.validacion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = MaximoBytesUtf8Validator.class)
@Target({ ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface MaximoBytesUtf8 {

	int value();

	String message() default "supera el máximo de bytes permitido";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
