/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * Broker.java
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: April 2014
 *==============================================================================
 */
package lexa.core.server;

import lexa.core.process.ProcessException;
import lexa.core.server.messaging.MessagingCaller;
import lexa.core.server.messaging.MessagingContainer;
import lexa.core.data.config.ConfigDataSet;
import lexa.core.data.DataSet;
import lexa.core.data.exception.DataException;
import lexa.core.expression.ExpressionException;
import lexa.core.expression.function.FunctionLibrary;
import lexa.core.logging.Logger;
import lexa.core.server.connection.Connection;
import lexa.core.server.connection.ConnectionList;
import lexa.core.server.context.*;
import lexa.core.server.messaging.MessagingStatus;

/**
 * A message broker to provide asynchronous message services to callers.
 *
 * <p>The server consists of a list of one or more services, each supporting one or
 * more processes.  As a general principle, the process agents will perform one specific
 * task.
 * <p>The configuration for a message broker is:
 * <pre>
 * name &lt;brokerName&gt;
 * [type &lt;threadModel&gt;]
 * [wildcard &lt;wildcardService&gt;]
 * [timeout &lt;timeout&gt;]
 * [brokerList {
 *   &lt;connectionName&gt; {
 *     ipAddress &lt;ipAddress&gt;
 *     port % &lt;port&gt;
 *   }
 * }
 * [logging {
 *   &lt;logging config&gt; {
 * }]
 * serviceList {
 *   &lt;serviceName&gt; {
 *       &lt;service config&gt;
 *   }
 *   [...]
 * }
 * </pre>
 * <p>Where:
 * <dl>
 * <dt>&lt;brokerName&gt;</dt><dd>a unique name for the message broker.</dd>
 * <dt>&lt;threadModel&gt;</dt><dd>indicate if the broker is run {@code inline} or {@code async};
 *		default is {@code async}</dd>
 * <dt>&lt;wildcardService&gt;</dt><dd>the name of a service in the service list;
 *      unknown services are rerouted to this service. [optional]</dd>
 * <dt>&lt;timeout&gt;</dt><dd>period in milliseconds after which messages will expire
 *      if no response has been received; a value of {@code 0} represents no timeout.
 *      [optional; default value is 30000.]
 * <dt>&lt;connectionName&gt;</dt><dd>a unique name for a remote message broker;
 *      the name "{@code local}" may not be used.</dd>
 * <dt>&lt;ipAddress&gt;</dt><dd>the IP address for a remote message broker.</dd>
 * <dt>&lt;port&gt;</dt><dd>the port for a remote message broker.</dd>
 * <dt>&lt;logging config&gt;</dt><dd>the configuration for the logging service;
 *      see {@link lexa.core.logging.LogLevels}</dd>
 * <dt>&lt;serviceName&gt;</dt><dd>a unique name within the broker for a service;
 *      the name {@code host} may not be used.</dd>
 * <dt>&lt;service config&gt;</dt><dd>the configuration for the service;
 *      see {@link Service}.</dd>
 * </dl>
 *
 * @author William
 * @since 2014-04
 */
public class Broker
		implements MessagingCaller
{
    /** Narrative name */
    private final String name;
    /** logger for events */
    private final Logger logger;
	/** Container to handle messages */
	private final MessagingContainer handler;
	/** a list of connection destinations */
	private final ConnectionList connectionList;

    /**
     * Create a new message broker from the supplied config.
     *
     * @param   config
     *          the configuration for the message broker.
     * @throws  DataException
     *          when there is a problem in the configuration.
     * @throws  ProcessException
     *          when an exception occurs within the processes.
	 * @throws  ExpressionException
     *          when an exception occurs within an expression
     */
    public Broker(ConfigDataSet config)
            throws DataException, ProcessException, ExpressionException
	{
        this(config, null);
    }
    /**
     * Create a new message broker from the supplied config.
     *
     * @param   config
     *          the configuration for the message broker.
     * @param   functionLibrary
     *          a library of functions to use for config driven processes.
     * @throws  DataException
     *          when there is a problem in the configuration.
     * @throws  ProcessException
     *          when an exception occurs within the processes.
	 * @throws  ExpressionException
	            when an exception occurs within an expression.
     */
    public Broker(ConfigDataSet config, FunctionLibrary functionLibrary)
			throws DataException, ProcessException, ExpressionException
	{
        this.name = config.getString(Config.NAME);
        this.logger = new Logger(Broker.class.getSimpleName() , this.name);
		ConfigDataSet loggingConfig = config.contains(Config.LOGGING) ?
                config.getDataSet(Config.LOGGING) :
                null;
        if (loggingConfig != null) {
            Logger.logLevels().setLogging(loggingConfig);
            loggingConfig.close();
        }

		Boolean inline = false;
		if (config.contains(Config.TYPE))
		{
			switch (config.getString(Config.TYPE))
			{
				case Value.TYPE_INLINE :
				{
					inline = true;
					break;
				}
				case Value.TYPE_ASYNC :
				{
					break; // already false
				}
				default :
				{
					throw new DataException("Broker type is not \"inline\" or \"async\"");
				}
			}
		}
		this.handler = BrokerHandler.container(config, functionLibrary, inline);

		ConfigDataSet brokerList = (config.contains(Config.BROKER_LIST)) ?
				config.getDataSet(Config.BROKER_LIST) :
				null;
		this.connectionList = new ConnectionList(brokerList);
		if (brokerList != null) {
			brokerList.close();
		}

        this.logger.info("Initialised message broker " + this.name);
    }

	public void start()
			throws ProcessException
	{
        this.logger.info("starting");
		this.connectionList.setBroker(this);
		this.handler.start(this);
		this.logger.info("started");
	}

    @Override
    public MessagingStatus getStatus()
    {
        return this.handler.getStatus();
    }
    /**
     * Get a connection for the local {@link Broker}
     *
     * @return  a new connection to this broker.
	 * @throws  ProcessException
     *          when an exception occurs getting a connection
     */
    public Connection getConnection()
            throws ProcessException {
        return this.getConnection(Value.LOCAL);
    }

    /**
     * Get a connection to a {@link MessageBroker}
     *
     * <p>The connection can be to this broker, using the name {@code LOCAL}, or
     * to any of the remote brokers defined in the {@code brokerList} section of
     * the configuration.
     *
     * <p> Calling {@code getConnection("LOCAL")} is equivalent to calling
     * {@link getConnection() getConnection()}
     *
     * @param   connectionName
     *          The name of a remote message broker; or {@code LOCAL} for the
     *          local message broker.
     *
     * @return  a new connection to the named message broker;
     *          or {@code null} if no connection could be established.
     *
     * @link MessageBroker#MessageBroker(lexa.core.data.config.ConfigDataSet) MessageBroker(ConfigDataSet config)
     */
    Connection getConnection(String connectionName)
            throws ProcessException {
		Connection connection = this.connectionList.newConnection(connectionName);
        this.logger.info("new connection " + connection.getId() + " to " + connectionName);
		return connection;
	}

	public void close()
	{
        this.logger.info("closing");
		//this.connectionList.setBroker(null);
		//this.brokerHandler.close();
		this.logger.info("closed");
	}

	public void inbound(DataSet data)
	{
		this.handler.inbound(data);
	}

    /**
     * Return an outbound message to the original caller.
     *
     * <p>The message must include the header field {@code "connectionId"}
     * to identify the connection for the message.
     *
     * @param   message
     *          the message being sent back to the caller.
     */
    public void outbound(DataSet message) {
        this.logger.message("MESSAGE_IO", "outbound" ,message,null);
        Integer cid = message.getInteger(Context.CONNECTION_ID);
        Integer sid = message.getInteger(Context.SOURCE_ID);
        Connection connection = this.connectionList.getConnection(cid);
        connection.reply(message);
//        // remove from the list of messages
//        for (int id = 0;
//                id < this.receivedTimes.size();
//                id++) {
//            MessageKey mk = this.receivedTimes.get(id);
//            if (mk.connection == cid) {
//                if (mk.session == sid) {
//                    this.receivedTimes.remove(id);
//                    break;
//                } else if (mk.session > sid) {
//                    break;
//                }
//            }
//        }
    }

}
