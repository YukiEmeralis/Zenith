package coffee.khyonieheart.eden.module.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import coffee.khyonieheart.eden.module.java.enums.BranchType;

/**
 * Represents a type of program "branch".
 * @author Khyonie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Branch 
{
	/**
	 * Data for the branch.
	 * @return Type of branch
	 */
    BranchType value() default BranchType.RELEASE;
}