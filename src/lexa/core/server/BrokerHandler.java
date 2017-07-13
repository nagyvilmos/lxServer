/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * BrokerHandler.java (lxServer)
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: August 2014
 *==============================================================================
 */
package lexa.core.server;

import java.util.*;
import lexa.core.data.config.ConfigDataSet;
import lexa.core.data.DataSet;
import lexa.core.data.DataType;
import lexa.core.data.config.ConfigDataArray;
import lexa.core.data.exception.DataException;
import lexa.core.expression.ExpressionException;
import lexa.core.expression.function.FunctionLibrary;
import lexa.core.logging.Logger;
import lexa.core.process.ProcessException;
import lexa.core.server.connection.Connection;
import lexa.core.server.context.*;
import lexa.core.server.messaging.*;

/**
 * Handler for a message broker.
 * @author william
 * @since 2014-08
 */
public class BrokerHandler
		implements MessagingHandler
{

    private final MessagingStatus status;

    @Override
    public MessagingStatus getStatus()
    {
        return this.status;
    }

	public static MessagingContainer container(ConfigDataSet config, FunctionLibrary functionLibrary, boolean inline)
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

	private BrokerHandler(ConfigDataSet config, FunctionLibrary functionLibrary, boolean inline)
			throws DataException, ProcessException, ExpressionException
	{
        // check we have the correct types
        config.validateType(
            Config.NAME,            DataType.STRING ,
            Config.SERVICE_LIST,    DataType.ARRAY
        );
		this.name = config.getString(Config.NAME);
        this.logger = new Logger(BrokerHandler.class.getSimpleName() , this.name);
        this.status=new MessagingStatus(this.name);
		this.wildcard = config.get(Config.WILDCARD,null).getString();

        this.timeout = config
                .get(Config.TIMEOUT,Value.DEFAULT_TIMEOUT)
                .getInteger();
        this.receivedTimes = new ArrayList();

		// if needed we can always get a URLClassLoader to allow
		// the explicit listing of jars to load.
		ClassLoader cl = ClassLoader.getSystemClassLoader();
        // create the services list
        this.services = new HashMap();
        ConfigDataArray serviceList = config.getArray(Config.SERVICE_LIST);
        for (int v=0; v <serviceList.size(); v++)
		{
            ConfigDataSet serviceConfig = serviceList.get(v).getDataSet();
            serviceConfig.validateType(Config.NAME, DataType.STRING);
            String sn = serviceConfig.getString(Config.NAME);
            if (this.services.containsKey(sn))
			{
                throw new DataException("Config contains duplicate service: " + sn + "@" + name);
            }
            MessagingContainer sc = Service.container(cl, serviceConfig, functionLibrary, inline);
			this.services.put(sn, sc);
            this.status.addChild(sc.getHandler().getStatus());
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
        this.status.addReceived();
		String serviceName = message.getString(Context.SERVICE);
		MessagingContainer serviceContainer = this.services.get(serviceName);
        if (serviceContainer == null) {
            if (this.wildcard != null) {
                serviceContainer = this.services.get(this.wildcard);
            }
            if (serviceContainer == null) {
                this.status.addError();
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
        this.status.addReplied();
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
        this.status.setActive(running);
        this.notifyAll();
    }

	Connection getConnection(String connectionName) throws ProcessException
	{
		return this.broker.getConnection(connectionName);
	}

}
