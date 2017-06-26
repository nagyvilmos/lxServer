/*==============================================================================
 * Lexa - Property of William Norman-Walker
 *------------------------------------------------------------------------------
 * ServerConfig.java
 *------------------------------------------------------------------------------
 * Author:  William Norman-Walker
 * Created: February 2017
 *==============================================================================
 */
package lxserver;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import lexa.core.data.DataSet;
import lexa.core.data.config.ConfigDataSet;
import lexa.core.data.exception.DataException;
import lexa.core.data.io.DataReader;
import lexa.core.expression.ExpressionException;
import lexa.core.expression.function.FunctionLibrary;
import lexa.core.logging.Logger;
import lexa.core.process.ProcessException;
import lexa.core.server.Broker;
import lexa.core.server.connection.Connection;
import lexa.core.server.context.Config;
import lexa.core.server.messaging.Message;
import lexa.test.TestAnnotation;
import lexa.test.TestClass;
import lexa.test.TestResult;

/**
 * Test handler for a config based server.
 * @author william
 * @since 2017-02
 */
@TestAnnotation(arguments = "getFileList", setUp = "setUpTestFile", tearDown = "tearDownTestFile")
public class ServerConfig
        extends TestClass
{

    private Broker broker;

    private Logger logger;
    private DataSet testCase;
    private DataSet testData;

    private final String testList;
    public ServerConfig(String testList)
    {
        this.testList = testList;
    }

    public String[] getFileList()
    {
        if (this.testList == null || this.testList.equals(""))
        {
            return new String[]{"test"};
        }
        return this.testList.split(" ");
    }

    public TestResult setUpTestFile(Object arg) throws FileNotFoundException, IOException
    {
        String fileName = (String)arg + ".server.lexa";
        this.testData =
                new DataReader(new File(fileName)).read();

        if (this.testData.contains(Config.LOG_FILE)) {
            lexa.core.logging.Logger.setLogWriter(
                    new File(this.testData.getString(Config.LOG_FILE)));
        }
        if (this.testData.contains(Config.LOGGING)) {
            lexa.core.logging.Logger.logLevels().setLogging(
                    this.testData.getDataSet(Config.LOGGING));
        }
        this.logger = new Logger("SERVER_TEST", fileName);
        this.logger.info("Test config", this.testData);

        return TestResult.notNull(this.testData);
    }

    public Object[] testList(Object arg)
    {
        if (this.testData.contains("test"))
        {
            return this.testData.getString("test").split(" ");
        }
        return this.testData.getDataSet("servers").keys();
    }

    public TestResult setUpServer(Object arg) throws ExpressionException, DataException, ProcessException
    {
        String testName=(String)arg;
        this.logger.info("Test:" + testName);
        this.testCase = this.testData.getDataSet("servers").getDataSet(testName);
        this.logger.info("Config", this.testCase);
        DataSet functions = this.testData.getDataSet("functions");
        DataSet testFunctions = this.testCase.getDataSet("functions");
        if (functions != null) {
            functions.put(testFunctions);
        } else {
            functions = testFunctions;
        }

        FunctionLibrary functionLibrary = new FunctionLibrary(functions);
        ConfigDataSet config = new ConfigDataSet(testCase.getDataSet("broker"));
        this.broker = new Broker(config, functionLibrary);
        config.close();
        this.broker.start();
        return TestResult.result(this.broker.getStatus().isActive());
    }

    @TestAnnotation(arguments = "testList", setUp = "setUpServer", tearDown = "tearDownServer")
    public TestResult testServerConfig(Object arg) throws ProcessException, InterruptedException
    {
        try
        {
            Connection connection = broker.getConnection();

            Message request = new Message(null,testCase.getDataSet("message"));
            int id = connection.submit(request);
            int slept = 0;
            while (!request.hasReply()) {
                if (slept == 1) {
                    logger.debug("sleeping");
                }
                logger.info("status",this.broker.getStatus().toData());
                slept++;
                this.wait(1000);
            }
            if (slept > 1) {
                logger.debug("woken");
            }

            DataSet reply = request.getReply();

            logger.info("reply", reply);
            logger.info("status",this.broker.getStatus().toData());

            return TestResult.result(this.testCase.getDataSet("expectedReply"), reply);

        } catch (InterruptedException | ProcessException ex) {
            logger.error("Exception during test", ex);
            throw ex;
        }
    }

    public TestResult tearDownServer(Object arg)
    {
        this.broker.close();
        this.broker=null;
        this.testCase=null;
        return TestResult.all(
                TestResult.isNull(this.broker),
                TestResult.isNull(this.testCase)
        );
    }

    public TestResult tearDownTestFile(Object arg)
    {
        this.testData=null;
        return TestResult.isNull(this.testData);
    }
}
