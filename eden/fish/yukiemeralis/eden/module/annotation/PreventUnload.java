package fish.yukiemeralis.eden.module.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import fish.yukiemeralis.eden.module.java.enums.CallerToken;

/**
 * Prevents a module from being disable and/or unloaded.<p>
 * 
 * However, this does not guarantee your module will not be disabled/unloaded, as the console can freely disable/unload modules.
 * If you want your modules to be unable to be disabled, pass in {@link CallerToken.EDEN},
 * which will prevent your module from being disabled by all except Eden internal processes.<p>
 * 
 * Default caller token is {@link CallerToken#CONSOLE}, which allows the console to disable modules, but not players.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface PreventUnload 
{
	/** */
    CallerToken value() default CallerToken.CONSOLE;
}
