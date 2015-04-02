/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * Connection.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: August 2013
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
import lexa.core.data.ConfigData;
import lexa.core.data.DataSet;
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
 * <p>The service is set up using config data provided by the {@link MessageBroker}.
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

	public static MessagingContainer container(String name, ClassLoader classLoader, ConfigData config, FunctionLibrary functionLibrary, boolean inline)
            throws DataException, ProcessException, ExpressionException
	{
		Service service =new Service(name, classLoader, config, functionLibrary, inline);
		
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
    /** Indicate if the service is in a running state */
    private boolean running;
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
    private Service (String name, ClassLoader classLoader, ConfigData config, FunctionLibrary functionLibrary, boolean inline)
            throws DataException, ProcessException, ExpressionException
	{

        this.name = name;
        this.logger = new Logger(Service.class.getSimpleName() , this.name);
        this.wildcard = config.getOptionalSetting(Config.WILDCARD);
        this.processes = new HashMap();
        ConfigData processList = config.getConfigData(Config.PROCESS_LIST);
        String[] processNames = processList.keys();
        for (String pn : processNames)
		{
			if (this.processes.containsKey(pn))
			{
                throw new DataException("Config contains duplicate process: " + pn + "@" + name);
			}
            ConfigData processConfig = processList.getConfigData(pn);
            this.processes.put(pn, ProcessAgent.container(pn,classLoader, processConfig, functionLibrary,inline));
					
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
	}

	@Override
	public void inbound(DataSet message)
	{
		// find out who this belongs to//
        this.logger.message("MESSAGE_IO", "inbound" ,message,null);
		String process = message.getString(Context.MESSAGE);
		MessagingContainer  pac = this.processes.get(process);
        if (pac == null) {
            if (this.wildcard != null) {
                pac = this.processes.get(this.wildcard);
            }
            if (pac == null) {
                bounceBack(message, "unknown message");
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
		this.container.outbound(message);
	}
	
	@Override
	public String getName()
	{
		return this.name;
	}
}
