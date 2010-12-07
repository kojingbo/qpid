/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.qpid.test.unit.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.client.AMQDestination;
import org.apache.qpid.client.AMQQueue;
import org.apache.qpid.client.AMQSession;
import org.apache.qpid.framing.AMQShortString;
import org.apache.qpid.server.queue.AMQQueueFactory;
import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.url.AMQBindingURL;
import org.apache.qpid.url.BindingURL;

/**
 * Test that the MaxRedelivery feature works as expected, allowing the client to reject
 * messages during rollback/recover whilst specifying they not be requeued if delivery
 * to an application has been attempted a specified number of times.
 * 
 * General approach: specify a set of messages which will cause the test client to then
 * deliberately rollback/recover the session after consuming, and monitor that they are
 * redlivered the specified number of times before the client rejects them without requeue
 * and then verify that they are not subsequently redelivered.
 * 
 * Additionally, the queue used in the test is configured for DLQ'ing, and the test verifies
 * that the messages rejected without requeue are then present on the appropriate DLQ.
 */
public class MaxDeliveryCountTest extends QpidTestCase
{
    private static final Logger _logger = Logger.getLogger(MaxDeliveryCountTest.class); 
    private Queue _queue;
    private boolean _failed;
    private String _failMsg;
    private static final int MSG_COUNT = 15;
    private static final int MAX_DELIVERY_COUNT = 2;
    private CountDownLatch _awaitCompletion;

    public void setUp() throws Exception
    {
        super.setUp();
        String queueName = getTestQueueName();
        
        //create an AMQQueue object using a BindingURL to set the Max Delivery Count for the consumer
        BindingURL burl = new AMQBindingURL("direct://amq.direct//" + queueName + "?maxdeliverycount='" + MAX_DELIVERY_COUNT + "'");
        _queue = new AMQQueue(burl);

        //declare the test queue, using some AMQSession hackery to enable DLQing
        Connection consumerConnection = getConnection();
        Session consumerSession = consumerConnection.createSession(false,Session.AUTO_ACKNOWLEDGE);
        final Map<String,Object> arguments = new HashMap<String, Object>();
        arguments.put(AMQQueueFactory.X_QPID_DLQ_ENABLED.asString(), true);
        ((AMQSession<?,?>) consumerSession).createQueue(new AMQShortString(queueName), false, false, false, arguments);
        ((AMQSession<?,?>) consumerSession).declareAndBind((AMQDestination) new AMQQueue("amq.direct",queueName));
        consumerConnection.close();

        //Create Producer put some messages on the queue
        Connection producerConnection = getConnection();
        producerConnection.start();

        Session producerSession = producerConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageProducer producer = producerSession.createProducer(_queue);

        for (int count = 1; count <= MSG_COUNT; count++)
        {
            Message msg = producerSession.createTextMessage("Message " + count);
            msg.setIntProperty("count", count);
            producer.send(msg);
        }

        producerConnection.close();
        
        _failed = false;
        _awaitCompletion = new CountDownLatch(1);
    }

    /**
     * Test that Max Redelivery is enforced when using onMessage() on a 
     * Client-Ack session.
     */
    public void testAsynchronousClientAckSession() throws Exception
    {
        final ArrayList<Integer> redeliverMsgs = new ArrayList<Integer>();
        redeliverMsgs.add(1);
        redeliverMsgs.add(2);
        redeliverMsgs.add(5);
        redeliverMsgs.add(14);

        doTest(Session.CLIENT_ACKNOWLEDGE, redeliverMsgs, false);
    }

    /**
     * Test that Max Redelivery is enforced when using onMessage() on a 
     * transacted session.
     */
    public void testAsynchronousTransactedSession() throws Exception
    {
        final ArrayList<Integer> redeliverMsgs = new ArrayList<Integer>();
        redeliverMsgs.add(1);
        redeliverMsgs.add(2);
        redeliverMsgs.add(5);
        redeliverMsgs.add(14);

        doTest(Session.SESSION_TRANSACTED, redeliverMsgs, false);
    }

    /**
     * Test that Max Redelivery is enforced when using onMessage() on an 
     * Auto-Ack session.
     */
    public void testAsynchronousAutoAckSession() throws Exception
    {
        final ArrayList<Integer> redeliverMsgs = new ArrayList<Integer>();
        redeliverMsgs.add(1);
        redeliverMsgs.add(2);
        redeliverMsgs.add(5);
        redeliverMsgs.add(14);

        doTest(Session.AUTO_ACKNOWLEDGE, redeliverMsgs, false);
    }
    
    /**
     * Test that Max Redelivery is enforced when using onMessage() on a 
     * Dups-OK session.
     */
    public void testAsynchronousDupsOkSession() throws Exception
    {
        final ArrayList<Integer> redeliverMsgs = new ArrayList<Integer>();
        redeliverMsgs.add(1);
        redeliverMsgs.add(2);
        redeliverMsgs.add(5);
        redeliverMsgs.add(14);

        doTest(Session.DUPS_OK_ACKNOWLEDGE, redeliverMsgs, false);
    }

    /**
     * Test that Max Redelivery is enforced when using recieve() on a 
     * Client-Ack session.
     */
    public void testSynchronousClientAckSession() throws Exception
    {
        final ArrayList<Integer> redeliverMsgs = new ArrayList<Integer>();
        redeliverMsgs.add(1);
        redeliverMsgs.add(2);
        redeliverMsgs.add(5);
        redeliverMsgs.add(14);

        doTest(Session.CLIENT_ACKNOWLEDGE, redeliverMsgs, true);
    }

    /**
     * Test that Max Redelivery is enforced when using recieve() on a 
     * transacted session.
     */
    public void testSynchronousTransactedSession() throws Exception
    {
        final ArrayList<Integer> redeliverMsgs = new ArrayList<Integer>();
        redeliverMsgs.add(1);
        redeliverMsgs.add(2);
        redeliverMsgs.add(5);
        redeliverMsgs.add(14);

        doTest(Session.SESSION_TRANSACTED, redeliverMsgs, true);
    }
    
    public void doTest(int deliveryMode, ArrayList<Integer> redeliverMsgs, boolean synchronous) throws Exception
    {
        Connection clientConnection = getConnection();
        
        boolean transacted = deliveryMode == Session.SESSION_TRANSACTED ? true : false;
        final Session clientSession = clientConnection.createSession(transacted, deliveryMode);

        MessageConsumer consumer = clientSession.createConsumer(_queue);

        assertEquals("The queue should have " + MSG_COUNT + " msgs at start",
                MSG_COUNT, ((AMQSession) clientSession).getQueueDepth((AMQDestination) _queue));

        clientConnection.start();

        int expectedDeliveries = MSG_COUNT + ((MAX_DELIVERY_COUNT -1) * redeliverMsgs.size());
        
        if(synchronous)
        {
            doSynchronousTest(clientSession, consumer, clientSession.getAcknowledgeMode(),
                    MAX_DELIVERY_COUNT, expectedDeliveries, redeliverMsgs);
        }
        else
        {
            addMessageListener(clientSession, consumer, clientSession.getAcknowledgeMode(),
                    MAX_DELIVERY_COUNT, expectedDeliveries, redeliverMsgs);

            try
            {
                if (!_awaitCompletion.await(20, TimeUnit.SECONDS))
                {
                    fail("Test did not complete in 20 seconds");
                }
            }
            catch (InterruptedException e)
            {
                fail("Unable to wait for test completion");
                throw e;
            }

            if(_failed)
            {
                fail(_failMsg);
            }
        }
        consumer.close();

        //check the source queue is now empty
        assertEquals("The queue should have 0 msgs left", 0, ((AMQSession<?,?>) clientSession).getQueueDepth((AMQDestination) _queue));
        
        //check the DLQ has the rejected-without-requeue messages
        String dlQueueName = getTestQueueName() + AMQQueueFactory.DEFAULT_DLQ_NAME_SUFFIX;
        assertEquals("The DLQ should have " + redeliverMsgs.size() + " msgs on it", redeliverMsgs.size()
                , ((AMQSession<?,?>) clientSession).getQueueDepth((AMQDestination) new AMQQueue("amq.direct", dlQueueName)));

        clientConnection.close();
    }

    private void addMessageListener(final Session session, final MessageConsumer consumer, final int deliveryMode, final int maxDeliveryCount,
                                    final int expectedTotalNumberOfDeliveries, final ArrayList<Integer> redeliverMsgs) throws JMSException
    {
        if(deliveryMode == org.apache.qpid.jms.Session.NO_ACKNOWLEDGE
                || deliveryMode == org.apache.qpid.jms.Session.PRE_ACKNOWLEDGE)
        {
            failAsyncTest("Max Delivery feature is not supported with this acknowledgement mode" +
            		      "when using asynchronous message delivery.");
        }

        consumer.setMessageListener(new MessageListener()
        {
            private int _deliveryAttempts = 0; //number of times given message(s) have been seen
            private int _numMsgsToBeRedelivered = 0; //number of messages to rollback/recover
            private int _totalNumDeliveries = 0;
            private int _expectedMessage = 1;

            public void onMessage(Message message)
            {
                if(_failed || _awaitCompletion.getCount() == 0L)
                {
                    //don't process anything else
                    return;
                }

                _totalNumDeliveries++;

                if (message == null)
                {
                    failAsyncTest("Should not get null messages");
                    return;
                }

                try
                {
                    int msgId = message.getIntProperty("count");
                    
                    _logger.info("Recieved Message: " + msgId);

                    //check the message is the one we expected
                    if(_expectedMessage != msgId)
                    {
                        failAsyncTest("Expected message " + _expectedMessage + " , got message " + msgId);
                        return;
                    }

                    _expectedMessage++;

                    //keep track of the overall deliveries to ensure we don't see more than expected
                    if(_totalNumDeliveries > expectedTotalNumberOfDeliveries)
                    {
                        failAsyncTest("Expected total of " + expectedTotalNumberOfDeliveries +
                                " message deliveries, reached " + _totalNumDeliveries);
                    }

                    //check if this message is one chosen to be rolled back / recovered
                    if(redeliverMsgs.contains(msgId))
                    {
                        _numMsgsToBeRedelivered++;

                        //check if next message is going to be rolled back / recovered too
                        if(redeliverMsgs.contains(msgId +1))
                        {
                            switch(deliveryMode)
                            {
                                case Session.SESSION_TRANSACTED:
                                    //skip on to next message immediately
                                    return;
                                case Session.CLIENT_ACKNOWLEDGE:
                                    //skip on to next message immediately
                                    return;
                                case Session.DUPS_OK_ACKNOWLEDGE:
                                    //fall through
                                case Session.AUTO_ACKNOWLEDGE:
                                    //must recover session now or onMessage will ack, so
                                    //just fall through the if
                                    break;
                            }
                        }

                        _deliveryAttempts++; //increment count of times the current rolled back/recovered message(s) have been seen

                        switch(deliveryMode)
                        {
                            case Session.SESSION_TRANSACTED:
                                session.rollback();
                                break;
                            case Session.CLIENT_ACKNOWLEDGE:
                                //fall through
                            case Session.DUPS_OK_ACKNOWLEDGE:
                                //fall through
                            case Session.AUTO_ACKNOWLEDGE:
                                session.recover();
                                break;
                        }

                        if( _deliveryAttempts >= maxDeliveryCount)
                        {
                            //the client should have rejected the latest messages upon then
                            //above recover/rollback, adjust counts to compensate
                            _deliveryAttempts = 0;
                        }
                        else
                        {
                            //the message(s) should be redelivered, adjust expected message
                            _expectedMessage -= _numMsgsToBeRedelivered;
                        }
                        
                        //reset count of messages expected to be redelivered
                        _numMsgsToBeRedelivered = 0;
                    }
                    else
                    {
                        //consume the message
                        switch(deliveryMode)
                        {
                            case Session.SESSION_TRANSACTED:
                                session.commit();
                                break;
                            case Session.CLIENT_ACKNOWLEDGE:
                                message.acknowledge();
                                break;
                            case Session.DUPS_OK_ACKNOWLEDGE:
                                //fall-through
                            case Session.AUTO_ACKNOWLEDGE:
                                //do nothing, onMessage will ack on exit.
                                break;
                        }
                    }

                    if (msgId == MSG_COUNT)
                    {
                        //if this is the last message let the test complete.
                        if (expectedTotalNumberOfDeliveries == _totalNumDeliveries)
                        {
                            _awaitCompletion.countDown();
                        }
                        else
                        {
                            failAsyncTest("Last message recieved, but we have not had the " +
                                        "expected number of total delivieres");
                        }
                    }
                }
                catch (JMSException e)
                {
                    failAsyncTest(e.getMessage());
                }
            }
        });
    }

    private void failAsyncTest(String msg)
    {
        _logger.error("Failing test because: " + msg);
        _failMsg = msg;
        _failed = true;
        _awaitCompletion.countDown();
    }

    private void doSynchronousTest(final Session session, final MessageConsumer consumer, final int deliveryMode, final int maxDeliveryCount,
            final int expectedTotalNumberOfDeliveries, final ArrayList<Integer> redeliverMsgs) throws JMSException, AMQException, InterruptedException
   {
        if(deliveryMode == Session.AUTO_ACKNOWLEDGE
                || deliveryMode == Session.DUPS_OK_ACKNOWLEDGE
                || deliveryMode == org.apache.qpid.jms.Session.PRE_ACKNOWLEDGE
                || deliveryMode == org.apache.qpid.jms.Session.NO_ACKNOWLEDGE)
        {
            fail("Max Delivery feature is not supported with this acknowledgement mode" +
                 "when using synchronous message delivery.");
        }

        int _deliveryAttempts = 0; //number of times given message(s) have been seen
        int _numMsgsToBeRedelivered = 0; //number of messages to rollback/recover
        int _totalNumDeliveries = 0;
        int _expectedMessage = 1;

        while(!_failed)
        {
            Message message = consumer.receive(1000);

            _totalNumDeliveries++;

            if (message == null)
            {
                fail("Should not get null messages");
                return;
            }

            try
            {
                int msgId = message.getIntProperty("count");

                _logger.info("Recieved Message: " + msgId);

                //check the message is the one we expected
                assertEquals("Unexpected message.", _expectedMessage, msgId);

                _expectedMessage++;

                //keep track of the overall deliveries to ensure we don't see more than expected
                assertTrue("Exceeded expected total number of deliveries.", 
                        _totalNumDeliveries <= expectedTotalNumberOfDeliveries );

                //check if this message is one chosen to be rolled back / recovered
                if(redeliverMsgs.contains(msgId))
                {
                    //keep track of the number of messages we will have redelivered
                    //upon rollback/recover
                    _numMsgsToBeRedelivered++;

                    if(redeliverMsgs.contains(msgId +1))
                    {
                        //next message is going to be rolled back / recovered too.
                        //skip ahead to it
                        continue;
                    }

                    _deliveryAttempts++; //increment count of times the current rolled back/recovered message(s) have been seen

                    switch(deliveryMode)
                    {
                        case Session.SESSION_TRANSACTED:
                            session.rollback();
                            break;
                        case Session.CLIENT_ACKNOWLEDGE:
                            session.recover();
                            
                            //sleep then do a synchronous op to give the broker
                            //time to resend all the messages
                            Thread.sleep(500);
                            ((AMQSession) session).sync();
                            break;
                    }

                    if( _deliveryAttempts >= maxDeliveryCount)
                    {
                        //the client should have rejected the latest messages upon then
                        //above recover/rollback, adjust counts to compensate
                        _deliveryAttempts = 0;
                    }
                    else
                    {
                        //the message(s) should be redelivered, adjust expected message
                        _expectedMessage -= _numMsgsToBeRedelivered;
                    }

                    //As we just rolled back / recovered, we must reset the 
                    //count of messages expected to be redelivered
                    _numMsgsToBeRedelivered = 0;
                }
                else
                {
                    //consume the message
                    switch(deliveryMode)
                    {
                        case Session.SESSION_TRANSACTED:
                            session.commit();
                            break;
                        case Session.CLIENT_ACKNOWLEDGE:
                            message.acknowledge();
                            break;
                    }
                }

                if (msgId == MSG_COUNT)
                {
                    //if this is the last message let the test complete.
                    assertTrue("Last message recieved, but we have not had the " +
                    		"expected number of total delivieres", 
                            expectedTotalNumberOfDeliveries == _totalNumDeliveries);

                    break;
                }
            }
            catch (JMSException e)
            {
                fail(e.getMessage());
            }
        }
   }
}
