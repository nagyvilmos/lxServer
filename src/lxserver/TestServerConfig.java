/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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

/**
 *
 * @author william
 */
@TestAnnotation(arguments = "getFileList", setUp = "setUpTestFile", tearDown = "tearDownTestFile")
public class TestServerConfig
        extends TestClass
{

    private Broker broker;

    private Logger logger;
    private DataSet testCase;
    private DataSet testData;

    private final String testList;
    public TestServerConfig(String testList)
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

    public Boolean setUpTestFile(Object arg) throws FileNotFoundException, IOException
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

        return true;
    }

    public Object[] testList(Object arg)
    {
        if (this.testData.contains("test"))
        {
            return this.testData.getString("test").split(" ");
        }
        return this.testData.getDataSet("servers").keys();
    }

    public Boolean setUpServer(Object arg) throws ExpressionException, DataException, ProcessException
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
        return true;
    }

    @TestAnnotation(arguments = "testList", setUp = "setUpServer", tearDown = "tearDownServer")
    public Boolean testServerConfig(Object arg) throws ProcessException, InterruptedException
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

            return reply.equals(this.testCase.getDataSet("expectedReply"));

        } catch (InterruptedException | ProcessException ex) {
            logger.error("Exception during test", ex);
            throw ex;
        }
    }

    public Boolean tearDownServer(Object arg)
    {
        this.broker.close();
        this.broker=null;
        this.testCase=null;
        return true;
    }

    public Boolean tearDownTestFile(Object arg)
    {
        this.logger.close();
        this.testData=null;
        return true;
    }
}
