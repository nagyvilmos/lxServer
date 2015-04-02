/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lexa.core.server.messaging;

import lexa.core.process.ProcessException;

/**
 *
 * @author william
 */
public interface MessagingHandler
		extends MessagingCaller
{
	public String getName();
	/**
	 *
	 * @param caller the caller into the container
	 * @param container the container
	 */
	public void start(MessagingCaller caller, MessagingContainer container)
			throws ProcessException;
}
