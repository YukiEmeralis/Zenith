package coffee.khyonieheart.eden.module.exception;

import coffee.khyonieheart.eden.module.annotation.StaticInitialize;

/**
 * Exception that is thrown when the initialization of a class with an {@link StaticInitialize} annotation fail due
 * to either a missing static init method, or invokation of such a method fails outright.
 * @since 1.7.3
 * @author Khyonie
 * @see {@link StaticInitialize}
 */
public class InvalidStaticInitException extends RuntimeException
{
    /**
     * Constructor that takes in a message and a causedBy throwable
     * @param message
     * @param causedBy
     */
    public InvalidStaticInitException(String message, Throwable causedBy)
    {
        super(message, causedBy);
    }
}