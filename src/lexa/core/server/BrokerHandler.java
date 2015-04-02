/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * BrokerHandler.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: August 2014
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
import lexa.core.server.context.*;
import lexa.core.server.messaging.*;

/**
 *
 * @author william
 */
public class BrokerHandler
		implements MessagingHandler
{

	public static MessagingContainer container(ConfigData config, FunctionLibrary functionLibrary, boolean inline)
			throws DataException, ProcessException, ExpressionException
	{
		BrokerHandler handler = new BrokerHandler(config, functionLibrary, inline);
		
		return inline ?
				new MessagingContainerInline(handler) :
				new MessagingContainerAsync(handler);
	}
    /** logger for events */
    private final Logger logger;
	/** times for messages being received */
    private final ArrayList<MessageKey> receivedTimes;
    /** Wildcard service for re-routing unknown messages */
    private final String wildcard;
    /** Timeout period for expiring messages */
    private final int timeout;
    /** The available services */
    private final Map<String, MessagingContainer> services;
	private boolean running;
	private MessagingContainer container;
	private final String name;
	private Broker broker;

	BrokerHandler(ConfigData config, FunctionLibrary functionLibrary, boolean inline)
			throws DataException, ProcessException, ExpressionException
	{
		this.name = config.getSetting(Config.NAME);
        this.logger = new Logger(BrokerHandler.class.getSimpleName() , this.name);

		this.wildcard = config.getOptionalSetting(Config.WILDCARD);

        this.timeout = (config.contains(Config.TIMEOUT)) ?
                config.getItem(Config.TIMEOUT).getInteger():
                Value.DEFAULT_TIMEOUT;
        this.receivedTimes = new ArrayList();

		// if needed we can always get a URLClassLoader to allow 
		// the explicit listing of jars to load.
		ClassLoader cl = ClassLoader.getSystemClassLoader();
        // create the services list
        this.services = new HashMap();
        ConfigData serviceList = config.getConfigData(Config.SERVICE_LIST);
        String[] serviceNames = serviceList.keys();
        for (String sn : serviceNames)
		{
            if (this.services.containsKey(sn))
			{
                throw new DataException("Config contains duplicate service: " + sn + "@" + name);
            }
		    ConfigData serviceConfig = serviceList.getConfigData(sn);
			this.services.put(sn, Service.container(sn, cl, serviceConfig, functionLibrary, inline));
					
            serviceConfig.close();
        }
        serviceList.close();
        if (this.wildcard != null && !this.services.containsKey(this.wildcard)) {
            throw new DataException("Config missing wildcard service: " + this.wildcard + "@" + name);
        }
	}

	@Override
	public void start(MessagingCaller caller, MessagingContainer container)
			throws ProcessException
	{
		this.broker = (Broker)caller;
		this.container = container;
		for (MessagingContainer s : this.services.values()) {
			s.start(this);
		}
		this.setRunning(true);
	}

	@Override
	public void inbound(DataSet message)
	{
		// find out who this belongs to//
        this.logger.message("MESSAGE_IO", "inbound" ,message,null);
		String serviceName = message.getString(Context.SERVICE);
		MessagingContainer serviceContainer = this.services.get(serviceName);
        if (serviceContainer == null) {
            if (this.wildcard != null) {
                serviceContainer = this.services.get(this.wildcard);
            }
            if (serviceContainer == null) {
                bounceBack(message, "unknown service");
                return;
            }
        }
        serviceContainer.inbound(message);
	}

	@Override
	public String getName()
	{
		return this.name;
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
	
    private class MessageKey {
        private final int connection;
        private final int session;
        private final long timestamp;

        private MessageKey(int connection, int message) {
            this.timestamp = new Date().getTime();
            this.connection = connection;
            this.session = message;
        }
    }
	
    /**
     * Method to set the running state of the broker and then notify of the change.
     *
     * @param   running
     *          {@code true} for the broker is running,
     *          otherwise {@code false}.
     */
    private synchronized void setRunning(boolean running) {
        this.logger.debug("setRunning " + running);
        this.running = running;
        this.notifyAll();
    }
	
	Connection getConnection(String connectionName) throws ProcessException
	{
		return this.broker.getConnection(connectionName);
	}

}
