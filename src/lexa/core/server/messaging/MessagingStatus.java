/*
 * =============================================================================
 * Lexa - Property of William Norman-Walker
 * -----------------------------------------------------------------------------
 * MessagingStatus.java
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: Month YEAR
 *------------------------------------------------------------------------------
 *==============================================================================
 */

package lexa.core.server.messaging;

import java.util.ArrayList;
import java.util.List;
import lexa.core.data.ArrayDataArray;
import lexa.core.data.ArrayDataSet;
import lexa.core.data.DataArray;
import lexa.core.data.DataSet;
import lexa.core.data.object.DataObject;

/**
 *
 * @author william
 */
public class MessagingStatus
        implements DataObject
{

    private final List<MessagingStatus> children;
    private int error;
    private final String name;
    private int received;
    private int replied;
    public MessagingStatus(String name)
    {
        this.name = name;
        this.children=new ArrayList();
        this.received=0;
        this.replied=0;
        this.error=0;
    }

    @Override
    public void fromData(DataSet ds)
    {
        throw new UnsupportedOperationException("MessagingStatus does not support fromData");
    }

    public boolean isActive()
    {
        throw new UnsupportedOperationException("MessagingStatus.isActive not supported yet.");
    }

    @Override
    public synchronized DataSet toData()
    {
        DataSet data = new ArrayDataSet()
                .put("name", this.name)
                .put("received", this.received)
                .put("replied", this.replied)
                .put("pending", this.received-this.replied-this.error)
                .put("error", this.error);
        if (this.children.size()>0)
        {
            DataArray childrenData = new ArrayDataArray();
            for (MessagingStatus child : this.children)
            {
                childrenData.add(child.toData());
            }
            data.put("children", childrenData);
        }
        return data;
    }

    public synchronized void addChild(MessagingStatus child)
    {
        this.children.add(child);
    }

    public synchronized void addError()
    {
        this.error++;
    }

    public synchronized void addReceived()
    {
        this.received++;
    }

    public synchronized void addReplied()
    {
        this.replied++;
    }

    public int getError()
    {
        return this.error;
    }

    public int getReceived()
    {
        return this.received;
    }

    public int getReplied()
    {
        return this.replied;
    }
}
