/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * LocalConnection.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: May 2013
 *--------------------------------------------------------------------------------
 * Change Log
 * Date:        By: Ref:        Description:
 * ----------   --- ----------  --------------------------------------------------
 * 2013.08.13   WNW -           Made Connection an interface and moved this out.
 *================================================================================
 */
package lexa.core.server.connection;

import lexa.core.data.DataSet;
import lexa.core.server.Broker;
import lexa.core.server.context.Value;

/**
 * A connection into the {@link MessageBroker} for submitting messages.
 * <p>Connections are created by the broker and used by the external process to submit messages.
 *
 * @since   2013.04
 * @author  William NW
 */
public class LocalConnection
        extends Connection {

    /**
     * Create a now connection into a broker with a given id.
     *
     * @param   messageBroker
     *          the broker for the connection to submit messages.
     * @param   id
     *          the unique id for the connection.
     */
    LocalConnection(Broker broker, int id) {
        super(broker, Value.LOCAL, id);
    }

	@Override
	void inbound(DataSet data)
	{
		this.getBroker().inbound(data);
	}

	@Override
	void start()
	{
		// does nothing
	}

	
}
