/*
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
 */
package org.apache.qpid.nclient.qpidapi;

import org.apache.qpid.nclient.exception.QpidException;

/**
 * This sessionís resources are control under the scope of a distributed transaction.
 *
 * Created by Arnaud Simon
 * Date: 20-Jul-2007
 * Time: 09:39:11
 */
public interface DtxSession extends Session
{

    /**
     * Get the XA resource associated with this session.
     *
     * @return this session XA resource.
     * @throws QpidException If the session fails to retrieve its associated XA resource
     *                       due to some error.
     */
    public javax.transaction.xa.XAResource getDTXResource()
            throws
            QpidException;
}
