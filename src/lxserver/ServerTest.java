/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * ServerTest.java
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: August 2013
 *==============================================================================
 */
package lxserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import lexa.core.data.config.ConfigDataSet;
import lexa.core.data.DataSet;
import lexa.core.data.io.DataReader;
import lexa.core.expression.function.FunctionLibrary;
import lexa.core.logging.Logger;
import lexa.core.server.connection.Connection;
import lexa.core.server.messaging.Message;
import lexa.core.server.Broker;
import lexa.core.server.context.Config;
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
public class ServerTest {

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

    public static void xmain(String ... args) {
        String fileName = "test.server.lexa";
        if (args != null && args.length > 0) {
            fileName = args[0];
        }
		System.out.println("Test server : " + fileName);
        DataSet file = null;
        try {
            file = new DataReader(new File(fileName)).read();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (file == null) {
            System.err.println("File not found, exiting.");
            return;
        }
        try {
            new ServerTest(file).testAll();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }

    }

    private final Logger logger;
    private final DataSet testData;

    private ServerTest(DataSet testData)
            throws FileNotFoundException {
        if (testData.contains(Config.LOG_FILE)) {
            lexa.core.logging.Logger.setLogWriter(
                    new File(testData.getString(Config.LOG_FILE)));
        }
        if (testData.contains(Config.LOGGING)) {
            lexa.core.logging.Logger.logLevels().setLogging(
					testData.getDataSet(Config.LOGGING));
        }
        this.logger = new Logger("SERVER_TEST", null);
        this.testData = testData;
        this.logger.info("Test config", this.testData);
    }

    private void testAll() {
        this.logger.info("Run all tests");
        String testAll = this.testData.getString("test");
        String[] tests =
                (testAll != null) ?
                        testAll.split(" ") :
                        testData.getDataSet("servers").keys();
		for (String test : tests)
		{
			this.test(test);
		}
        this.logger.info("Full test run complete");
    }

    private synchronized void test(String testName)  {
        this.logger.info("Test:" + testName);
        DataSet testCase = this.testData.getDataSet("servers").getDataSet(testName);
        this.logger.info("Config", testCase);
        DataSet functions = this.testData.getDataSet("functions");
        DataSet testFunctions = testCase.getDataSet("functions");
        if (functions != null) {
            functions.put(testFunctions);
        } else {
            functions = testFunctions;
        }

        Broker broker = null;
        try {
			FunctionLibrary functionLibrary = new FunctionLibrary(functions);
            ConfigDataSet config = new ConfigDataSet(testCase.getDataSet("broker"));
            broker = new Broker(config, functionLibrary);
            config.close();
            broker.start();
            Connection connection = broker.getConnection();

            Message request = new Message(null,testCase.getDataSet("message"));
            int id = connection.submit(request);
            int slept = 0;
            while (!request.hasReply()) {
                if (slept == 1) {
                    logger.debug("sleeping");
                }
                slept++;
                this.wait(1000);
            }
            if (slept > 1) {
                logger.debug("woken");
            }

            DataSet reply = request.getReply();

            logger.info("reply", reply);

        } catch (Exception ex) {
            logger.error("Exception during test", ex);
        } finally {
            if (broker != null) {
                broker.close();
            }
        }
    }
}
