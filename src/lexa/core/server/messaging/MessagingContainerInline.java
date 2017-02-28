/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lexa.core.server.messaging;

import lexa.core.data.DataSet;
import lexa.core.queue.FIFOQueue;
import lexa.core.queue.Queue;
import lexa.core.process.ProcessException;

/**
 *
 * @author william
 */
public class MessagingContainerInline
		implements MessagingContainer
{
	private final MessagingHandler handler;
	// you'll see, you'll see
	private final Queue outbound;
	private MessagingCaller caller;
	private boolean sending;
	public MessagingContainerInline(MessagingHandler handler)
	{
		this.handler = handler;
		this.outbound = new FIFOQueue();
		this.sending = false;
	}

	@Override
	public void inbound(DataSet message)
	{
		this.sending = true;
		this.handler.inbound(message);
		this.sending = false;
		flushOutbound();
	}

	@Override
	public void outbound(DataSet message)
	{
		this.outbound.add(message);
		flushOutbound();
	}

	@Override
	public void start(MessagingCaller caller)
			throws ProcessException
	{
		this.caller = caller;
		this.handler.start(caller, this);
	}

	private void flushOutbound()
	{
		if (this.sending)
		{
			return; // sending up so don't send down
		}
		while (!this.outbound.isEmpty())
		{
			this.caller.outbound(this.outbound.get());
		}
	}

    public MessagingHandler getHandler()
    {
        return this.handler;
    }

    public MessagingStatus getStatus()
    {
        return this.handler.getStatus();
    }
}
