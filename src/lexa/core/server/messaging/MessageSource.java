/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package lexa.core.server.messaging;

/**
 *
 * @author William
 * @since YYYY-MM
 */
public interface MessageSource  {

    public void messageClosed(Message message);
    public void replyReceived(Message message);
    public void updateReceived(Message message);
}
