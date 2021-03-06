/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * Service.java (lxServer)
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: August 2013
 *==============================================================================
 */
package lexa.core.server;

import lexa.core.process.ProcessException;
import java.util.*;
import lexa.core.data.config.ConfigDataSet;
import lexa.core.data.DataSet;
import lexa.core.data.DataType;
import lexa.core.data.config.ConfigDataArray;
import lexa.core.data.exception.DataException;
import lexa.core.expression.ExpressionException;
import lexa.core.expression.function.FunctionLibrary;
import lexa.core.logging.Logger;
import lexa.core.server.connection.Connection;
import lexa.core.server.context.Config;
import lexa.core.server.context.Context;
import lexa.core.server.messaging.*;

/**
 * A service set up using configuration.
 *
 * <p>The service is set up using config data provided by the {@link Broker}.
 * Each service contains a set of {@link Process} agents that handle the messages.
 * <p>The configuration for a service is:
 * <pre>
 * &lt;serviceName&gt; {
 *   [wildcard &lt;wildcardProcess&gt;]
 *   processList {
 *     &lt;processName&gt; {
 *       &lt;process config&gt;
 *     }
 *     [...]
 *   }
 * }
 * </pre>
 * <p>Where:
 * <dl>
 * <dt>&lt;serviceName&gt;</dt><dd>a unique name within the broker for a service;
 *      the name {@code host} may not be used.</dd>
 * <dt>&lt;wildcardProcess&gt;</dt><dd>the name of a process in the process list;
 *      unknown processes are rerouted to this process. [optional]</dd>
 * <dt>&lt;processName&gt;</dt><dd>a unique name within the service for a process.</dd>
 * <dt>&lt;process config&gt;</dt><dd>the configuration for the process; see {@link Process}.</dd>
 * </dl>
 *
 * @author William
 * @since 2013-04
 */
public class Service
		implements MessagingHandler {

    private final MessagingStatus status;

    @Override
    public MessagingStatus getStatus()
    {
        return this.status;
    }

	public static MessagingContainer container(ClassLoader classLoader, ConfigDataSet config, FunctionLibrary functionLibrary, boolean inline)
            throws DataException, ProcessException, ExpressionException
	{
		Service service =new Service(classLoader, config, functionLibrary, inline);

		return inline ?
				new MessagingContainerInline(service) :
				new MessagingContainerAsync(service);
	}

    /** logger for events */
    private final Logger logger;
    /** Name of the service; used for routing messages */
    private final String name;
    /** Wildcard process for re-routing unknown messages */
    private final String wildcard;
    /** A link back to the broker that created this service */
    private Broker messageBroker;
    /** The available processes */
    private final Map<String, MessagingContainer> processes;
	private MessagingContainer container;
	private BrokerHandler broker;

    /**
     * Create a new service from the supplied config.
     *
     * @param   name
     *          The name of the service.
     * @param   config
     *          the configuration for the service.
     * @throws  DataException
     *          when there is a problem in the configuration.
     * @throws  ProcessException
     *          when an exception occurs within the processes.
     */
    private Service (ClassLoader classLoader, ConfigDataSet config, FunctionLibrary functionLibrary, boolean inline)
            throws DataException, ProcessException, ExpressionException
	{
        config.validateType(
                Config.NAME,            DataType.STRING,
                Config.PROCESS_LIST,    DataType.ARRAY
        );
        this.name = config.getString(Config.NAME);
        this.logger = new Logger(Service.class.getSimpleName() , this.name);
        this.status = new MessagingStatus(this.name);
        this.wildcard = config.get(Config.WILDCARD,null).getString();
        this.processes = new HashMap();
        ConfigDataArray processList = config.getArray(Config.PROCESS_LIST);
        for (int p=0; p < processList.size(); p++)
		{
            ConfigDataSet processConfig = processList.get(p).getDataSet();
            processConfig.validateType(Config.NAME, DataType.STRING);
            String pn = processConfig.getString(Config.NAME);
			if (this.processes.containsKey(pn))
			{
                throw new DataException("Config contains duplicate process: " + pn + "@" + name);
			}
            MessagingContainer pc =
                    ProcessAgent.container(classLoader, processConfig, functionLibrary,inline);
            this.processes.put(pn, pc);
            this.status.addChild(pc.getHandler().getStatus());
            processConfig.close();
        }
        processList.close();
        if (this.wildcard != null && !this.processes.containsKey(this.wildcard)) {
            throw new DataException("Config missing wildcard process: " + this.wildcard + "@" + this.name);
        }
        this.logger.info("Service initialised, wildcard ='" + this.wildcard +"'");
    }

	@Override
	public void start(MessagingCaller caller, MessagingContainer container)
			throws ProcessException
	{
		this.broker = (BrokerHandler)caller;
		this.container = container;
		for (MessagingContainer pac : this.processes.values())
		{
			pac.start(this);
		}
        this.status.setActive(true);
	}

	@Override
	public void inbound(DataSet message)
	{
		// find out who this belongs to//
        this.logger.message("MESSAGE_IO", "inbound" ,message,null);
        this.status.addReceived();
		String process = message.getString(Context.MESSAGE);
		MessagingContainer  pac = this.processes.get(process);
        if (pac == null) {
            if (this.wildcard != null) {
                pac = this.processes.get(this.wildcard);
            }
            if (pac == null) {
                this.status.addError();
                bounceBack(message, "unknown message " + process + " on " + this.getName());
                return;
            }
        }
        pac.inbound(message);
	}

	Connection getConnection(String connectionName)
			throws ProcessException
	{
		return this.broker.getConnection(connectionName);
	}

    /**
     * Bounces a message back to the caller with a simple {@code return} value.
     * @param   message
     *          the original inbound message
     * @param   returnMessage
     *          a message to return to the caller
     */
    private void bounceBack(DataSet message, String returnMessage) {
        this.logger.debug("bounceBack " + returnMessage , message);
        message
				.put(Context.RETURN, returnMessage)
				.put(Context.CLOSE, true);
        this.outbound(message);
    }

	@Override
	public void outbound(DataSet message)
	{
        this.status.addReplied();
		this.container.outbound(message);
	}

	@Override
	public String getName()
	{
		return this.name;
	}
}
