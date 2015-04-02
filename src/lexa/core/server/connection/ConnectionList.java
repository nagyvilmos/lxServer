/*
 * ================================================================================
 * Lexa - Property of William Norman-Walker
 * --------------------------------------------------------------------------------
 * ConnectionList.java
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
package lexa.core.server.connection;

import java.util.HashMap;
import java.util.Map;
import lexa.core.data.ConfigData;
import lexa.core.data.exception.DataException;
import lexa.core.server.Broker;
import lexa.core.server.context.Value;
import lexa.core.process.ProcessException;

/**
 *
 * @author william
 */
public class ConnectionList
{
    /** The ID of the last session created */
    private int lastSessionId;
	/** the config for all the remote hosts */
	private final HashMap<String, RemoteHost> remoteHosts;
    /** All active connections */
    private final Map<Integer, Connection> connections;

	private Broker broker;

	public ConnectionList(ConfigData config)
			throws DataException
	{
        //load the list of remote hosts:
        this.remoteHosts = new HashMap<String, RemoteHost>();
        if (config != null) {
            String[] brokerNames = config.keys();
            for (int b = 0;
                    b < brokerNames.length;
                    b++) {
                String brokerName = brokerNames[b];
                ConfigData brokerConfig = config.getConfigData(brokerName);
                if (this.remoteHosts.containsKey(brokerName)) {
                    throw new DataException("Config contains duplicate remote hosts: " + brokerName);
                }
                this.remoteHosts.put(brokerName, new RemoteHost(brokerName, brokerConfig));
                brokerConfig.close();
            }
        }
		this.lastSessionId = 0;
        this.connections = new HashMap<Integer, Connection>();
	}

	public synchronized Connection newConnection(String connectionName) throws ProcessException
	{
        int id = ++this.lastSessionId;
        Connection connection;
        if (Value.LOCAL.equals(connectionName)) {
            connection = new LocalConnection(this.broker, id);
        } else {
            RemoteHost remote = this.remoteHosts.get(connectionName);

            if(remote == null) {
                throw new ProcessException("Unknown connection destination " + connectionName);
            }
            connection = new RemoteConnection(this.broker, id, remote);
        }
        this.connections.put(connection.getId(),connection);
		connection.start();
        return connection;
	}

	public Connection getConnection(int connection)
	{
		return this.connections.get(connection);
	}

	public void close()
	{
        // tell the connections you're dead:
        for (Connection c : this.connections.values()) {
            c.close();
        }
	}

	public void setBroker(Broker broker)
	{
		this.broker = broker;
	}
	
}
