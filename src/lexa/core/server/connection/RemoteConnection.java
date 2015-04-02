/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package lexa.core.server.connection;


import java.io.IOException;
import lexa.core.comms.Session;
import lexa.core.comms.SessionListener;
import lexa.core.data.DataSet;
import lexa.core.data.exception.DataException;
import lexa.core.server.Broker;
import lexa.core.process.ProcessException;

/**
 *
 * @author William Norman-Walker
 * @since YYYY-MM
 */

public class RemoteConnection
        extends Connection
		implements SessionListener {

    /** the broker for submitting messages */
    private final Session session;

    /**
     * Create a now connection into a broker with a given id.
     *
     * @param   messageBroker
     *          the broker for the connection to submit messages.
     * @param   id
     *          the unique id for the connection.
     * @param   remote
     *          provides the session.
     */
    RemoteConnection(Broker broker, int id, RemoteHost remote)
            throws ProcessException {
		super(broker,remote.getName(),id);
        this.session = remote.getSession();
    }

    @Override
    public void close() {
        super.close();
		this.session.close();
    }

	@Override
	void inbound(DataSet data)
	{
		try
		{
			this.session.send(data);
		}
		catch (DataException | IOException ex)
		{
			this.logger.error("inbound send failed", data, ex);
			
		}
	}

	@Override
	public void message(Session session, DataSet data)
	{
		this.reply(data);
	}

	@Override
	void start()
	{
		this.session.setSessionListener(this);
	}
	
	
}
