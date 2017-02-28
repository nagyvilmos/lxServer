/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * ProcessAgent.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: April 2013
 *--------------------------------------------------------------------------------
 * Change Log
 * Date:        By: Ref:        Description:
 * ----------   --- ----------  --------------------------------------------------
 * 2015-03-11	WNW	2015-03		Updated in line with new lxData
 *================================================================================
 */
package lexa.core.server;

import lexa.core.process.ProcessException;
import java.util.*;
import lexa.core.data.*;
import lexa.core.data.config.ConfigDataSet;
import lexa.core.data.exception.DataException;
import lexa.core.expression.ExpressionException;
import lexa.core.expression.function.FunctionLibrary;
import lexa.core.logging.Logger;
import lexa.core.queue.FIFOQueue;
import lexa.core.queue.Queue;
import lexa.core.server.connection.Connection;
import lexa.core.server.context.Config;
import lexa.core.server.context.Context;
import lexa.core.process.factory.ProcessFactory;
import lexa.core.server.messaging.*;
import lexa.core.process.LexaProcess;
import lexa.core.process.Status;

/**
 * A process agent set up using configuration.
 * <p>The process is set up using config data provided by the {@link ConfigService}.
 * Each process agent handles a single message type.
 * <p>The process can be used for handling multiple messages by name but the action
 * will be the same based on the configuration.
 * <p>The configuration block for a process is:
 * <pre>
 * &lt;processName&gt; {
 *   [connectionName &lt;connectionName&gt;]
 *   classPath &lt;classPath&gt;
 *   [config {
 *     &lt;process config&gt;
 *   }
 * }
 * </pre>
 * <p>Where:
 * <dl>
 * <dt>&lt;processName&gt;</dt><dd>a unique name within the service for a process.</dd>
 * <dt>&lt;connectionName&gt;</dt><dd>the name of a message broker that this process will
 *      connect to for sending messages; the name may be {@code local}, for a loop back connection,
 *      or any named broker from the {@link MessageBroker} {@code brokerList} configuration block.
 *      [optional]</dd>
 * <dt>&lt;classPath&gt;</dt><dd>the class path for the {@link ProcessFactory}.</dd>
 * <dt>&lt;process config&gt;</dt><dd>the configuration for the process; see the implementations
 *      of {@link Process} for details.</dd>
 * </dl>
 *
 * @author William
 * @since 2013-04
 */
public class ProcessAgent
		implements MessagingHandler,
				MessageSource
{

    private final MessagingStatus status;

    @Override
    public MessagingStatus getStatus()
    {
        return this.status;
    }

	public static MessagingContainer container(ClassLoader classLoader, ConfigDataSet config, FunctionLibrary functionLibrary, boolean inline)
            throws DataException,
				ProcessException,
				ExpressionException
	{
		ProcessAgent processAgent = new ProcessAgent(classLoader, config, functionLibrary);
		return inline ?
					new MessagingContainerInline(processAgent) :
					new MessagingContainerAsync(processAgent);
	}

    /** logger for events */
    private final Logger logger;
    /** Name of the process; used for routing messages */
    private final String name;
	private final Queue inbound;
	private final Queue forwardReplies;
    /** the maximum number of child processes for this process */
    private final int maxProcesses;
    /** factory to produce processes */
    private final ProcessFactory factory;
    /** a list of processes used to handle messages */
    private final List<LexaProcess> processes;
    /** all outbound messages currently being processed */
    private final HashMap<Integer, Message> outboundMessages;
    /** the name of a {@link MessageBroker} that this can connect to for forwarding messages */
    private final String connectionName;
    /** the {@link MessageBroker} that this can connect to for forwarding messages */
    private Connection connection;
    /** a link back to the containing service */
    private MessagingContainer container;
    /** Indicate if the service is in a running state */
    private boolean running;
	private Service service;
	private boolean active;

    /**
     * Create a new process from the supplied config.
     *
     * @param   name
     *          The name of the process.
     * @param   config
     *          the configuration for the process.
     * @throws  DataException
     *          when there is a problem in the configuration.
     * @throws  ProcessException
     *          when an exception occurs within the processes.
     */
	private ProcessAgent(ClassLoader classLoader, ConfigDataSet config, FunctionLibrary functionLibrary)
            throws DataException, ProcessException, ExpressionException
	{
        this.name = config.getString(Config.NAME);
        this.status = new MessagingStatus(this.name);
        this.logger = new Logger(ProcessAgent.class.getSimpleName(), this.name);
		this.inbound = new FIFOQueue();  // TODO - lose the queue, it's queued above here
        this.connectionName = config.get(Config.CONNECTION_NAME,null).getString();
        this.processes = new LinkedList();
        this.outboundMessages = new HashMap();
        this.maxProcesses =config.get(Config.MAX_PROCESSES, 1).getInteger();
        this.factory = new ProcessFactory(
				classLoader, config, functionLibrary);
        // create the first process, this will ensure the config is clean and the factory sound.
        this.processes.add(factory.instance());

		this.forwardReplies = new FIFOQueue();
		this.active = false;
    }

	@Override
	public void start(MessagingCaller caller,MessagingContainer container)
			throws ProcessException
	{
		this.service = (Service)caller;
		this.container = container;
		if (this.connectionName != null)
		{
			this.connection = this.service.getConnection(this.connectionName);
		}
	}

	@Override
	public void inbound(DataSet message)
	{
		this.logger.debug("inbound", message);
        this.status.addReceived();
        this.inbound.add(message);
		process();
	}

    @Override
    public void messageClosed(Message message) {
        int sid = message.getSourceId();
        this.logger.debug("messageClosed " + sid);
        this.outboundMessages.remove(sid);
    }

    @Override
    public synchronized void replyReceived(Message message) {
		DataSet reply = new ArrayDataSet()
				.put(Context.SOURCE_REF, message.getSourceReference())
				.put(Context.REPLY, message.getReply());
		this.logger.debug("replyRecieved", reply);
		this.forwardReplies.add(reply);
		process();
	}

	@Override
	public void updateReceived(Message message)
	{
		throw new UnsupportedOperationException("lexa.core.server.ProcessAgent.updateReceived:void not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	private synchronized void process()
	{
		if (this.active)
		{
			return;
		}

		this.active = true;
		while (this.active)
		{
			this.processAction();
			if (this.forwardReplies.isEmpty())
			{
				this.active = false;
			}
			else
			{
				this.processForwardReplies();
			}
		}
	}
	private synchronized void processAction()
	{
		// check the processes states:
		boolean busy;
		this.logger.debug("processAction.start");
		do
		{
			busy = false;
			for (LexaProcess process : this.processes)
			{
				try {
					Status status = process.getStatus();
					if (status.acceptRequests() && !this.inbound.isEmpty()) {
						DataSet request = this.inbound.get();
						this.logger.debug("Submit request",request);
						process.handleRequest(request);
						busy = true;
					}
					if (status.requestPending()) {
						this.processForwardRequests(process.getRequests());
						busy = true;
					}
					if (status.waitingProcess()) {
						process.process();
						busy = true;
					}
					if (status.replyReady()) {
						this.outbound(process.getReply());
						busy = true;
					}
				}
				catch (ProcessException ex)
				{
					this.logger.error(this.getName() + '.' +  process.getId() + "failed.", ex);
					this.status.addError();
				}
			}
			if (!this.inbound.isEmpty() && this.processes.size() < this.maxProcesses)
			{
				try
				{
					this.processes.add(this.factory.instance());
				}
				catch (ProcessException | DataException | ExpressionException ex)
				{
					this.logger.error("Cannot create new worker", ex);
				}
			}
		} while (busy);
		this.logger.debug("processAction.end");
	}

	@Override
	public String getName()
	{
		return this.name;
	}

	@Override
	public void outbound(DataSet message)
	{
		this.logger.debug("outbound", message);
        this.status.addReplied();
		this.container.outbound(message);
	}

    /**
     * Process requests from the processes to the forward connection.
     * <p>The requests are in the format defined by {@link Process#getRequests()}
     *
     * @param   requests
     *          a set of requests to be sent.
     * @throws  ProcessException
     *          there was an error processing the messages:
     *          <ul>
     *          <li>no forward connection</li>
     *          </ul>
     */
    private synchronized void processForwardRequests(DataSet requests)
            throws ProcessException {
        this.logger.debug("Handle requests",requests);
        if (this.connection == null) {
			throw new ProcessException("No forward connection for service", requests);
        }

        Integer sourceRef = requests.getInteger(Context.SOURCE_REF);
        DataSet messageList = requests.getDataSet(Context.MESSAGE_LIST);
        for (DataItem item : messageList) {
            Message message = new Message(this,
					new ArrayDataSet(item.getDataSet())
							.put(Context.SOURCE_REF,sourceRef));
            int mid = this.connection.submit(message);
            this.outboundMessages.put(mid, message);
        }
    }

	private synchronized void processForwardReplies()
	{
		while (!this.forwardReplies.isEmpty())
		{
			DataSet message = this.forwardReplies.get();
			this.logger.debug("processForwardReplies",message);

			int ref = message.getInteger(Context.SOURCE_REF);
			DataSet reply = message.getDataSet(Context.REPLY);


			LexaProcess process = null;
			for (LexaProcess w : this.processes) {
				if (w.getId() == ref) {
					process = w;
					break;
				}
			}
			if (process == null) {
				this.logger.error("Reply received with no source process " + ref, reply, null);
			}
			try {
				process.handleReply(reply);
			} catch (ProcessException ex) {
				this.logger.error("Unable to submit reply", reply, ex);
			}
		}
		processAction(); // picks it up again
	}
}
