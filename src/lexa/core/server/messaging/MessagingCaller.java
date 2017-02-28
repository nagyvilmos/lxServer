/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package lexa.core.server.messaging;

import lexa.core.data.DataSet;

/**
 *
 * @author william
 */
public interface MessagingCaller
{
	public void inbound(DataSet message);

	public void outbound(DataSet message);

    public MessagingStatus getStatus();
}
