/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package lexa.core.server.connection;

import java.io.IOException;
import java.net.*;
import lexa.core.comms.Session;
import lexa.core.data.ConfigData;
import lexa.core.data.exception.DataException;
import lexa.core.process.ProcessException;
import lexa.core.server.context.Config;

/**
 *
 * @author Felhasználó
 */
class RemoteHost {
    private final String name;
    private final InetAddress ipAddress;
    private final Integer port;

    RemoteHost(String name, ConfigData config)
            throws DataException {
        this.name = name;
		String ip = config.getSetting(Config.HOST);
        try {
            this.ipAddress = Inet4Address.getByName(ip);
        } catch (UnknownHostException ex) {
            throw new DataException("Unable to determine remote host " + this.name + "@" + ip );
        }
        this.port = config.getItem(Config.PORT).getInteger();
    }

	String getName() {
		return this.name;
				
	} 
    /**
     * get a session to the remote host.
     *
     * @return  a session to the remote host
     */
    Session getSession()
            throws ProcessException {
        try {
            Socket socket = new Socket(this.ipAddress, this.port);
            return new Session(socket);
        } catch (IOException ex) {
            throw new ProcessException("Unable to create session for host " + this.name, ex);
        }
    }

}
