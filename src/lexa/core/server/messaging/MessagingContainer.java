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
public interface MessagingContainer
		extends MessagingCaller
{
    public MessagingHandler getHandler();

    public void start(MessagingCaller caller)
			throws ProcessException;

}
