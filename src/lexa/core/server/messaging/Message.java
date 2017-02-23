/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * Message.java
 *--------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: May 2013
 *--------------------------------------------------------------------------------
 * Change Log
 * Date:        By: Ref:        Description:
 * ----------   --- ----------  --------------------------------------------------
 * 2015-03-11	WNW	2015-03		Updated in line with new lxData
 *================================================================================
 */
package lexa.core.server.messaging;

import lexa.core.data.DataSet;
import lexa.core.data.SealedDataSet;
import lexa.core.data.ArrayDataSet;
import lexa.core.server.connection.Connection;
import lexa.core.server.context.Context;

/**
 * Message builder class.
 *
 * User: William
 * Date: 07/05/13
 * Time: 13:13
 * To change this template use File | Settings | File Templates.
 */
public class Message {
    /** The source of the message to notified of any updates */
    private final MessageSource messageSource;
    /** The name of the message's destination service */
    private final String service;
    /** The type of message being requested */
    private final String message;
    /** The content of the request */
    private final DataSet request;
    /** The content of the reply */
    private final DataSet reply;
    /** Source path for the message */
    private final DataSet source;
    /** A reference number unique to the source */
    private final Integer sourceReference;
    /** Indicates if a reply has been received */
    private boolean replyReceived;
    /** Indicates if a new reply has been received since last reading the reply stack. */
    private boolean newReplyReceived;
    private int connectionId;

    public String getService() {
        return service;
    }

    public String getMessage() {
        return message;
    }

    public DataSet getRequestContext() {
        return new SealedDataSet(request);
    }

    public DataSet getSource() {
        return new SealedDataSet(source);
    }

    public Integer getSourceReference() {
        return sourceReference;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public int getSourceId() {
        return sourceId;
    }
    private int sourceId;

    public Message(MessageSource messageSource, DataSet message) {
        this(messageSource,
                message.getString(Context.SERVICE),
                message.getString(Context.MESSAGE),
                message.getDataSet(Context.REQUEST),
                message.getDataSet(Context.SOURCE),
                message.getInteger(Context.SOURCE_REF));
    }
    /**
     * Create a new message to submit to the {@link MessageBroker}.
     *
     * <p>Each message is self contained with an outbound request and inbound reply.
     *
     * @param   service
     *          The name of the message's destination service
     * @param   message
     *          The type of message being requested
     * @param   request
     *          The content of the request
     */
    public Message (String service,
            String message,
            DataSet request) {
        this(null, service, message, request, null, null);
    }

    /**
     * Create a new message to submit to the {@link MessageBroker}.
     *
     * <p>Each message is self contained with an outbound request and inbound reply.
     *
     * @param   service
     *          The name of the message's destination service
     * @param   message
     *          The type of message being requested
     * @param   request
     *          The content of the request
     * @param   sourceReference
     *          A reference number unique to the source
     */
    public Message (String service,
            String message,
            DataSet request,
            Integer sourceReference) {
        this(null, service, message, request, null, sourceReference);
    }

    /**
     * Create a new message to submit to the {@link MessageBroker}.
     *
     * <p>Each message is self contained with an outbound request and inbound reply.
     *
     * @param   messageSource
     *          The source of the message to notified of any updates
     * @param   service
     *          The name of the message's destination service
     * @param   message
     *          The type of message being requested
     * @param   request
     *          The content of the request
     * @param   source
     *          Source path for the message
     * @param   sourceReference
     *          A reference number unique to the source
     */
    public Message (MessageSource messageSource,
            String service,
            String message,
            DataSet request,
            DataSet source,
            Integer sourceReference) {
        this.messageSource = messageSource;
        this.service = service;
        this.message = message;
        this.request = request;
        this.source = source;
        this.reply = new ArrayDataSet();
        this.sourceReference = sourceReference;
        this.replyReceived = false;
        this.newReplyReceived = false;
    }

    /**
     * Add a reply from the {@link MessageBroker} for this message.
     *
     * @param   reply
     *          the reply data for the message.
     */
    public void addReply (DataSet reply) {
		this.reply.put(reply);

        boolean newReply = !this.replyReceived;
        this.replyReceived = true;
        this.newReplyReceived = true; // this one is set to false each time the replies are read.

        if (this.messageSource != null) {
            if (newReply) {
                this.messageSource.replyReceived(this);
            } else {
                this.messageSource.updateReceived(this);
            }
        }
        if (reply.getBoolean(Context.CLOSE)) {
            this.close();
        }
    }

    /**
     * Get a header block for the message.
     *
     * <p>The header is in the format:
     * <pre>
     * service  &lt;service&gt;
     * message  &lt;message&gt;
     * connectionId  &lt;connectionId&gt;
     * sourceId  &lt;sourceId&gt;
     * [source {
     *   service  &lt;service&gt;
     *   message  &lt;message&gt;
     *   connectionId  &lt;connectionId&gt;
     *   sourceId  &lt;sourceId&gt;
     *   [source {
     *     ...
     *   }]
     * }]
     * <pre>
     *
     * @param   connectionId
     *          the id for the {@link Connection} submitting the message.
     * @param   sourceId
     *          the sequence id for the message from the {@link Connection}.
     * @return  a {see DataSet} containing the header information.
     */
    public DataSet getHeader(int connectionId, int sourceId) {
        this.connectionId = connectionId;
        this.sourceId = sourceId;
        DataSet header = new ArrayDataSet()
				.put(Context.SERVICE,this.service)
				.put(Context.MESSAGE, this.message)
				.put(Context.CONNECTION_ID, connectionId)
				.put(Context.SOURCE_ID, sourceId);
        if (this.sourceReference != null) {
            header.put(Context.SOURCE_REF, this.sourceReference);
        }
        if (this.source != null) {
            header.put(Context.SOURCE, this.source);
        }
        return header;
    }

    /**
     * Get the reply data received to date for the message.
     *
     * <p> The replies are built up, with new data adding to what has already been received.
     *
     * @return  {@code null} if no reply has been received, otherwise all the reply data.
     */
    public DataSet getReply() {
        if (!this.replyReceived) {
            return null;
        }
        this.newReplyReceived = false;
        return new SealedDataSet(this.reply);
    }

    /**
     * Get a request block for the message.
     *
     * <p>The request is in the format:
     * <pre>
     * &lt;header&gt;
     * request {
     *   &lt;request&gt;
     * }
     * <pre>
     *
     * @param   connectionId
     *          the id for the {@link Connection} submitting the message.
     * @param   sourceId
     *          the sequence id for the message from the {@link Connection}.
     * @return  a {see DataSet} containing the request.
     */
    public DataSet getRequest(int connectionId, int sourceId) {
        DataSet requestData = this.getHeader(connectionId,sourceId)
				.put(Context.REQUEST, this.request);
        return requestData;
    }

    /**
     * Indicates if a new reply has been received since last reading the reply stack.
     *
     * @return  {@code true} if a reply has been received since the last
     *          call to {@link getReply() getReply()},
     *          otherwise {@code false}.
     */
    public boolean hasNewReply() {
        return this.newReplyReceived;
    }

    /**
     * Indicates if a reply has been received.
     *
     * @return  {@code true} if a reply has been received,
     *          otherwise {@code false}.
     */
    public boolean hasReply() {
        return this.replyReceived;
    }

    public void close() {
        if (this.messageSource != null) {
            this.messageSource.messageClosed(this);
        }
    }
}
