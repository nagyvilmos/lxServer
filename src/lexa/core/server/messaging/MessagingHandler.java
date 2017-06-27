/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * MessagingHandler.java
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: April 2015
 *==============================================================================
 */
package lexa.core.server.messaging;

import lexa.core.process.ProcessException;

/**
 * Interface for a class that handles messages
 * @author william
 * @since 2015-04
 */
public interface MessagingHandler
		extends MessagingCaller
{
	public String getName();
	/**
	 * Start the message handler
	 * @param   caller
     *          the caller into the container
	 * @param   container
     *          the container
     * @throws  ProcessException
     *          when an exception is encountered
	 */
	public void start(MessagingCaller caller, MessagingContainer container)
			throws ProcessException;

    @Override
    public MessagingStatus getStatus();
}
