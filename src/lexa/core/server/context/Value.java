/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * Value.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: July 2013
 *--------------------------------------------------------------------------------
 * Change Log
 * Date:        By: Ref:        Description:
 * ----------   --- ----------  --------------------------------------------------
 * -            -   -           -
 *================================================================================
 */
package lexa.core.server.context;

/**
 * Provides constants for field values used in the Lexa MessageBroker.
 *
 * @author William
 * @since 2013-07
 */
public class Value {
    public static final String CLASS_CONFIG	    = "Config";
    /** "{@code Echo}" */
    public static final String CLASS_ECHO	    = "Echo";
    /** "{@code Echo}" */
    public static final String CLASS_PASS_THROUGH
												= "PassThrough";
    /** The value "{@code CLOSE_MESSAGE}" */
    public static final String CLOSE_MESSAGE    = "CLOSE_MESSAGE";
    /** The value "{@code local}" */
    public static final String LOCAL            = "local";
    /** The value {@code 30000} or 30 seconds */
    public static final int DEFAULT_TIMEOUT     = 30000;
	
	public static final String HOST_SERVICE		= "host";
	public static final String TYPE_ASYNC		= "async";
	public static final String TYPE_INLINE		= "inline";
}
