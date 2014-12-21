package lc.common.util.data;

/**
 * Represents a predicate (boolean-valued function) of any number of untyped or
 * typed arguments. This is a functional interface whose functional method is
 * test(Object[]).
 * 
 * @author AfterLifeLochie
 */
public interface AnyPredicate {

	/**
	 * Evaluates this predicate on the given argument.
	 * 
	 * @param t
	 *            the input argument(s), if any
	 * @return true if the input argument(s) matches the predicate, otherwise
	 *         false
	 */
	public boolean test(Object[] t);

}