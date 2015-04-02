/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * Connection.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: May 2013
 *--------------------------------------------------------------------------------
 * Change Log
 * Date:        By: Ref:        Description:
 * ----------   --- ----------  --------------------------------------------------
 * 2013.08.30   WNW -           Made abstract and moved concrete to LocalConnection
 * 2015-03-11	WNW	2015-03		Updated in line with new lxData
 *================================================================================
 */
package lexa.core.server.connection;

import java.util.HashMap;
import java.util.Map;
import lexa.core.data.DataSet;
import lexa.core.data.SimpleDataSet;
import lexa.core.logging.Logger;
import lexa.core.server.Broker;
import lexa.core.server.messaging.Message;
import lexa.core.server.context.Context;
import lexa.core.server.context.Value;

/**
 * A connection into the {@link MessageBroker} for submitting messages.
 * <p>Connections are created by the broker and used by the external process to submit messages.
 *
 * @since   2013.04
 * @author  William NW
 */
public abstract class Connection {

	/** message logger */
	protected final Logger logger;
    /** unique id for the connection */
    private final int id;
    /** the broker for submitting messages */
    private final Broker broker;
    /** the current messages for the connection. */
    private final Map<Integer,Message> messages;
    /** the id of the last message sent */
    private int lastMessage;
	
	Connection(Broker broker,String name,  int id) {
		this.logger = new Logger(Connection.class.getSimpleName(), name + "#" + id);
		this.broker = broker;
		this.id = id;
        this.messages = new HashMap<Integer, Message>();
        this.lastMessage = 0;
	}

    /**
     * Add a reply for a message.
     *
     * @param   reply
     *          The reply to a message.
     */
    public synchronized void reply(DataSet reply) {
        int sid = reply.getInteger(Context.SOURCE_ID);
        Message message = this.messages.get(sid);
        if (message == null) {
            return;
        }
        message.addReply(reply);

        if (reply.getBoolean(Context.CLOSE)) {
            this.messages.remove(sid);
        }
    }

    /**
     * Notify the connection that a message has timed out
     * @param   sid
     *          the id of the message
     */
    public void timeout(int sid) {
        DataSet timeoutMessage = new SimpleDataSet()
				.put(Context.SOURCE_ID,sid)
				.put(Context.RETURN,"message timed out with no response");
		this.logger.error("message timeout", timeoutMessage,null);
        this.reply(timeoutMessage);
        this.closeMessage(sid);
    }

	public Integer getId() {
		return this.id;
	}

	protected Broker getBroker() {
		return this.broker;
	}

    /**
     * Submit a message to the message broker
     * <p>The message is given a unique session id for the connection.
     * There is no guarantee that all the session ids will be unique across
     * different sessions, but the connection and session ids together will
     * always be unique for the life of the broker..

     * @param   message
     *          a {@link Message} to submit for processing.
     * @return  the session id for the message.
     */
    public synchronized int submit(Message message)
	{
        int sid = ++this.lastMessage;
        DataSet request = message.getRequest(this.getId(), sid);
        this.messages.put(sid,message);
        this.inbound(request);
		return sid;
	}

    /**
     * Close the connection.
     */
	public synchronized void close()
	{
        if (this.messages.isEmpty()) {
            return;
        }
        Integer[] keys = this.messages.keySet().toArray(new Integer[0]);
        for (int k = 0;
                k < keys.length;
                k++) {
            this.closeMessage(keys[k]);
        }
    }


    /**
     * Close a message session.
     * @param   sid
     *          the session id for the message.
     */
    public synchronized void closeMessage(int sid) {
        // this should submit a message back to the host.
        Message message = this.messages.remove(sid);
        if (message == null) {
            return;
        }
        DataSet close = message.getHeader(this.getId(), sid);

        close.put(Context.SYSTEM_REQUEST, Value.CLOSE_MESSAGE);
        this.inbound(close);
        message.close();
    }
	
	/**
	 * Send a message to the inbound processor
	 * @param	data
	 *			the message data being submitted
	 */
	abstract void inbound(DataSet data);
	
	abstract void start();
}
