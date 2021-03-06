/*
 * Copyright (c) 2008-2019 Haulmont. All rights reserved.
 * Use is subject to license terms, see http://www.cuba-platform.com/commercial-software-license for details.
 */

package com.haulmont.cuba.gui.components.validation;

import com.haulmont.bali.util.ParamsMap;
import com.haulmont.chile.core.datatypes.DatatypeRegistry;
import com.haulmont.cuba.core.global.BeanLocator;
import com.haulmont.cuba.core.global.Messages;
import com.haulmont.cuba.core.global.UserSessionSource;
import com.haulmont.cuba.gui.components.ValidationException;
import com.haulmont.cuba.gui.components.validation.numbers.NumberConstraint;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

import static com.haulmont.cuba.gui.components.validation.ValidatorHelper.getNumberConstraint;

/**
 * NegativeOrZero validator checks that value should be a less than or equal 0.
 * <p>
 * For error message it uses Groovy string and it is possible to use '$value' key for formatted output.
 * <p>
 * In order to provide your own implementation globally, create a subclass and register it in {@code web-spring.xml},
 * for example:
 * <pre>
 *    &lt;bean id="cuba_NegativeOrZeroValidator" class="com.haulmont.cuba.gui.components.validation.NegativeOrZeroValidator" scope="prototype"/&gt;
 *    </pre>
 * Use {@link BeanLocator} when creating the validator programmatically.
 *
 * @param <T> BigDecimal, BigInteger, Long, Integer, Double, Float
 */
@Component(NegativeOrZeroValidator.NAME)
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class NegativeOrZeroValidator<T extends Number> extends AbstractValidator<T> {

    public static final String NAME = "cuba_NegativeOrZeroValidator";

    public NegativeOrZeroValidator() {
    }

    /**
     * Constructor for custom error message. This message can contain '$value' key for formatted output.
     * <p>
     * Example: "Value '$value' should be less than or equal to 0".
     *
     * @param message error message
     */
    public NegativeOrZeroValidator(String message) {
        this.message = message;
    }

    @Inject
    protected void setMessages(Messages messages) {
        this.messages = messages;
    }

    @Inject
    protected void setDatatypeRegistry(DatatypeRegistry datatypeRegistry) {
        this.datatypeRegistry = datatypeRegistry;
    }

    @Inject
    protected void setUserSessionSource(UserSessionSource userSessionSource) {
        this.userSessionSource = userSessionSource;
    }

    @Override
    public void accept(T value) throws ValidationException {
        // consider null value is valid
        if (value == null) {
            return;
        }

        NumberConstraint constraint = getNumberConstraint(value);
        if (constraint == null) {
            throw new IllegalArgumentException("NegativeOrZeroValidator doesn't support following type: '" + value.getClass() + "'");
        }

        if (!constraint.isNegativeOrZero()) {
            String message = getMessage();
            if (message == null) {
                message = messages.getMainMessage("validation.constraints.negativeOrZero");
            }

            String formattedValue = formatValue(value);
            throw new ValidationException(getTemplateErrorMessage(message, ParamsMap.of("value", formattedValue)));
        }
    }
}
