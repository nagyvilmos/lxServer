/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * ServerTest.java (lxServer)
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: August 2013
 *==============================================================================
 */
package lxserver;

import lexa.core.data.DataSet;
import lexa.test.TestClass;
import lexa.test.TestRun;

/**
 * Test bed for lxServer.
 * <p>Uses a {@link DataSet} file to store test servers.  This should be used like this to run simple
 * tests on single components and not to test fully functional servers.
 * See the file {@code test.server.lexa} to see the full test structure.
 *
 * @author William
 * @since 2013-08
 * @see lexa.core.server
 */
public class ServerTest
{
    /**
     * Entry point to launch the tests.
     *
     * @param args Server to test, or {@code -all}
     */
    public static void main(String ... args)
    {
        TestClass[] tests = new TestClass[]{
            new ServerConfig(
                (args != null && args.length > 0) ?
                        args[0] :
                        null
            )
        };
        System.out.println(
                new TestRun(tests)
                        .execute()
                        .getReport()
        );
    }
}
