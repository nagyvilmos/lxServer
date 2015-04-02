/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lexa.core.server.messaging;

import lexa.core.data.DataSet;
import lexa.core.logging.Logger;
import lexa.core.queue.FIFOQueue;
import lexa.core.queue.Queue;
import lexa.core.process.ProcessException;

/**
 *
 * @author william
 */
public class MessagingContainerAsync
		extends Thread
		implements MessagingContainer
{

	private final Logger logger;
	private final MessagingHandler handler;
	private final Queue inbound;
	private final Queue outbound;
	private MessagingCaller caller;
	private boolean notified;
	private boolean running;

	public MessagingContainerAsync(MessagingHandler handler)
	{
		this.logger = new Logger(MessagingContainerAsync.class.getSimpleName(), handler.getName());
		this.handler = handler;
		this.inbound = new FIFOQueue();
		this.outbound = new FIFOQueue();
	}
	
	@Override
	public void inbound(DataSet message)
	{
		this.inbound.add(message);
		this.messageNotify();
	}

	@Override
	public void outbound(DataSet message)
	{
		this.outbound.add(message);
		this.messageNotify();
	}
	
	@Override
	public void run()
	{
		this.logger.info("thread started");
		while (this.running)
		{
			this.process();
		}
		this.logger.info("thread stopped");
	}
	
	@Override
	public void start(MessagingCaller caller)
			throws ProcessException
	{
		this.caller = caller;
		this.handler.start(caller, this);
		this.setRunning(true);
		super.start();
	}

	private synchronized void messageNotify()
	{
		this.notified = true;
		this.notifyAll();
	}

	private void setRunning(boolean running)
	{
		this.running = running;
		this.messageNotify();
	}

	private synchronized void process()
	{
		// nothing
		if (this.inbound.isEmpty() && this.outbound.isEmpty() )
		{
			try
			{
				while (!this.notified)
				{
					this.wait();
				}
				this.notified = false;
			}
			catch (InterruptedException ex)
			{
				this.logger.error(ex.getMessage(), ex);
			}
		}
		// one in
		if (!this.inbound.isEmpty())
		{
			this.handler.inbound(this.inbound.get());
		}
		// one out
		if (!this.outbound.isEmpty())
		{
			this.caller.outbound(this.outbound.get());
		}
	}
}
