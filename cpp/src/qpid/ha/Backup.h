#ifndef QPID_HA_BACKUP_H
#define QPID_HA_BACKUP_H

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

#include "LogPrefix.h"
#include "Settings.h"
#include "qpid/Url.h"
#include "qpid/sys/Mutex.h"
#include <boost/shared_ptr.hpp>

namespace qpid {

namespace broker {
class Broker;
class Link;
}

namespace ha {
class Settings;
class BrokerReplicator;
class HaBroker;

/**
 * State associated with a backup broker. Manages connections to primary.
 *
 * THREAD SAFE
 */
class Backup
{
  public:
    Backup(HaBroker&, const Settings&);
    ~Backup();
    void setBrokerUrl(const Url&);

  private:
    bool isSelf(const Address& a) const;
    Url linkUrl(const Url&) const;
    void initialize(const Url&);

    LogPrefix logPrefix;
    sys::Mutex lock;
    HaBroker& haBroker;
    broker::Broker& broker;
    Settings settings;
    boost::shared_ptr<broker::Link> link;
    boost::shared_ptr<BrokerReplicator> replicator;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_BACKUP_H*/
