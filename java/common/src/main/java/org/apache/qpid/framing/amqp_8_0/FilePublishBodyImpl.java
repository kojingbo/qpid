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

/*
 * This file is auto-generated by Qpid Gentools v.0.1 - do not modify.
 * Supported AMQP version:
 *   8-0
 */

package org.apache.qpid.framing.amqp_8_0;

import org.apache.qpid.codec.MarkableDataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.apache.qpid.framing.*;
import org.apache.qpid.AMQException;

public class FilePublishBodyImpl extends AMQMethodBody_8_0 implements FilePublishBody
{
    private static final AMQMethodBodyInstanceFactory FACTORY_INSTANCE = new AMQMethodBodyInstanceFactory()
    {
        public AMQMethodBody newInstance(MarkableDataInput in, long size) throws AMQFrameDecodingException, IOException
        {
            return new FilePublishBodyImpl(in);
        }
    };

    public static AMQMethodBodyInstanceFactory getFactory()
    {
        return FACTORY_INSTANCE;
    }

    public static final int CLASS_ID =  70;
    public static final int METHOD_ID = 60;

    // Fields declared in specification
    private final int _ticket; // [ticket]
    private final AMQShortString _exchange; // [exchange]
    private final AMQShortString _routingKey; // [routingKey]
    private final byte _bitfield0; // [mandatory, immediate]
    private final AMQShortString _identifier; // [identifier]

    // Constructor
    public FilePublishBodyImpl(MarkableDataInput buffer) throws AMQFrameDecodingException, IOException
    {
        _ticket = readUnsignedShort( buffer );
        _exchange = readAMQShortString( buffer );
        _routingKey = readAMQShortString( buffer );
        _bitfield0 = readBitfield( buffer );
        _identifier = readAMQShortString( buffer );
    }

    public FilePublishBodyImpl(
                                int ticket,
                                AMQShortString exchange,
                                AMQShortString routingKey,
                                boolean mandatory,
                                boolean immediate,
                                AMQShortString identifier
                            )
    {
        _ticket = ticket;
        _exchange = exchange;
        _routingKey = routingKey;
        byte bitfield0 = (byte)0;
        if( mandatory )
        {
            bitfield0 = (byte) (((int) bitfield0) | (1 << 0));
        }

        if( immediate )
        {
            bitfield0 = (byte) (((int) bitfield0) | (1 << 1));
        }

        _bitfield0 = bitfield0;
        _identifier = identifier;
    }

    public int getClazz()
    {
        return CLASS_ID;
    }

    public int getMethod()
    {
        return METHOD_ID;
    }

    public final int getTicket()
    {
        return _ticket;
    }
    public final AMQShortString getExchange()
    {
        return _exchange;
    }
    public final AMQShortString getRoutingKey()
    {
        return _routingKey;
    }
    public final boolean getMandatory()
    {
        return (((int)(_bitfield0)) & ( 1 << 0)) != 0;
    }
    public final boolean getImmediate()
    {
        return (((int)(_bitfield0)) & ( 1 << 1)) != 0;
    }
    public final AMQShortString getIdentifier()
    {
        return _identifier;
    }

    protected int getBodySize()
    {
        int size = 3;
        size += getSizeOf( _exchange );
        size += getSizeOf( _routingKey );
        size += getSizeOf( _identifier );
        return size;
    }

    public void writeMethodPayload(DataOutput buffer) throws IOException
    {
        writeUnsignedShort( buffer, _ticket );
        writeAMQShortString( buffer, _exchange );
        writeAMQShortString( buffer, _routingKey );
        writeBitfield( buffer, _bitfield0 );
        writeAMQShortString( buffer, _identifier );
    }

    public boolean execute(MethodDispatcher dispatcher, int channelId) throws AMQException
	{
    return ((MethodDispatcher_8_0)dispatcher).dispatchFilePublish(this, channelId);
	}

    public String toString()
    {
        StringBuilder buf = new StringBuilder("[FilePublishBodyImpl: ");
        buf.append( "ticket=" );
        buf.append(  getTicket() );
        buf.append( ", " );
        buf.append( "exchange=" );
        buf.append(  getExchange() );
        buf.append( ", " );
        buf.append( "routingKey=" );
        buf.append(  getRoutingKey() );
        buf.append( ", " );
        buf.append( "mandatory=" );
        buf.append(  getMandatory() );
        buf.append( ", " );
        buf.append( "immediate=" );
        buf.append(  getImmediate() );
        buf.append( ", " );
        buf.append( "identifier=" );
        buf.append(  getIdentifier() );
        buf.append("]");
        return buf.toString();
    }

}
