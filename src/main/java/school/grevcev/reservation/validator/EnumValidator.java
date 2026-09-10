package school.grevcev.reservation.validator;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class EnumValidator implements ConstraintValidator<ValidEnum, Object> {

    private Class<? extends Enum<?>> enumClass;

    @Override
    public void initialize(ValidEnum constraintAnnotation) {
        this.enumClass = constraintAnnotation.enumClass();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // @NotNull обрабатывает null отдельно
        }

        // Если значение уже enum — проверяем, что оно правильного типа
        if (value instanceof Enum<?>) {
            return enumClass.isInstance(value);
        }

        // Если String — проверяем по имени константы
        if (value instanceof String) {
            String strValue = ((String) value).trim();
            if (strValue.isEmpty()) {
                return false;
            }
            Object[] constants = enumClass.getEnumConstants();
            if (constants == null) {
                return false;
            }
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(strValue)) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }
}
