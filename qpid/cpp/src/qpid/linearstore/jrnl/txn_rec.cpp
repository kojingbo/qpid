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

#include "qpid/linearstore/jrnl/txn_rec.h"

#include <cassert>
#include <cerrno>
#include <cstdlib>
#include <cstring>
#include <iomanip>
#include "qpid/linearstore/jrnl/jerrno.h"
#include "qpid/linearstore/jrnl/jexception.h"
#include <sstream>

namespace mrg
{
namespace journal
{

txn_rec::txn_rec():
//        _txn_hdr(),
        _xidp(0),
        _buff(0)
//        _txn_tail()
{
    ::txn_hdr_init(&_txn_hdr, 0, QLS_JRNL_VERSION, 0, 0, 0);
    ::rec_tail_init(&_txn_tail, 0, 0, 0);
}

txn_rec::txn_rec(const uint32_t magic, const uint64_t rid, const void* const xidp,
        const std::size_t xidlen/*, const bool owi*/):
//        _txn_hdr(magic, RHM_JDAT_VERSION, rid, xidlen, owi),
        _xidp(xidp),
        _buff(0)
//        _txn_tail(_txn_hdr)
{
    ::txn_hdr_init(&_txn_hdr, magic, QLS_JRNL_VERSION, 0, rid, xidlen);
    ::rec_tail_copy(&_txn_tail, &_txn_hdr._rhdr, 0);
}

txn_rec::~txn_rec()
{
    clean();
}

void
txn_rec::reset(const uint32_t magic)
{
    _txn_hdr._rhdr._magic = magic;
    _txn_hdr._rhdr._rid = 0;
    _txn_hdr._xidsize = 0;
    _xidp = 0;
    _buff = 0;
    _txn_tail._xmagic = ~magic;
    _txn_tail._rid = 0;
}

void
txn_rec::reset(const uint32_t magic, const  uint64_t rid, const void* const xidp,
        const std::size_t xidlen/*, const bool owi*/)
{
    _txn_hdr._rhdr._magic = magic;
    _txn_hdr._rhdr._rid = rid;
//    _txn_hdr.set_owi(owi);
    _txn_hdr._xidsize = xidlen;
    _xidp = xidp;
    _buff = 0;
    _txn_tail._xmagic = ~magic;
    _txn_tail._rid = rid;
}

uint32_t
txn_rec::encode(void* wptr, uint32_t rec_offs_dblks, uint32_t max_size_dblks)
{
    assert(wptr != 0);
    assert(max_size_dblks > 0);
    assert(_xidp != 0 && _txn_hdr._xidsize > 0);

    std::size_t rec_offs = rec_offs_dblks * JRNL_DBLK_SIZE;
    std::size_t rem = max_size_dblks * JRNL_DBLK_SIZE;
    std::size_t wr_cnt = 0;
    if (rec_offs_dblks) // Continuation of split dequeue record (over 2 or more pages)
    {
        if (size_dblks(rec_size()) - rec_offs_dblks > max_size_dblks) // Further split required
        {
            rec_offs -= sizeof(txn_hdr_t);
            std::size_t wsize = _txn_hdr._xidsize > rec_offs ? _txn_hdr._xidsize - rec_offs : 0;
            std::size_t wsize2 = wsize;
            if (wsize)
            {
                if (wsize > rem)
                    wsize = rem;
                std::memcpy(wptr, (const char*)_xidp + rec_offs, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            rec_offs -= _txn_hdr._xidsize - wsize2;
            if (rem)
            {
                wsize = sizeof(_txn_tail) > rec_offs ? sizeof(_txn_tail) - rec_offs : 0;
                wsize2 = wsize;
                if (wsize)
                {
                    if (wsize > rem)
                        wsize = rem;
                    std::memcpy((char*)wptr + wr_cnt, (char*)&_txn_tail + rec_offs, wsize);
                    wr_cnt += wsize;
                    rem -= wsize;
                }
                rec_offs -= sizeof(_txn_tail) - wsize2;
            }
            assert(rem == 0);
            assert(rec_offs == 0);
        }
        else // No further split required
        {
            rec_offs -= sizeof(txn_hdr_t);
            std::size_t wsize = _txn_hdr._xidsize > rec_offs ? _txn_hdr._xidsize - rec_offs : 0;
            if (wsize)
            {
                std::memcpy(wptr, (const char*)_xidp + rec_offs, wsize);
                wr_cnt += wsize;
            }
            rec_offs -= _txn_hdr._xidsize - wsize;
            wsize = sizeof(_txn_tail) > rec_offs ? sizeof(_txn_tail) - rec_offs : 0;
            if (wsize)
            {
                std::memcpy((char*)wptr + wr_cnt, (char*)&_txn_tail + rec_offs, wsize);
                wr_cnt += wsize;
#ifdef RHM_CLEAN
                std::size_t rec_offs = rec_offs_dblks * JRNL_DBLK_SIZE;
                std::size_t dblk_rec_size = size_dblks(rec_size() - rec_offs) * JRNL_DBLK_SIZE;
                std::memset((char*)wptr + wr_cnt, RHM_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
            }
            rec_offs -= sizeof(_txn_tail) - wsize;
            assert(rec_offs == 0);
        }
    }
    else // Start at beginning of data record
    {
        // Assumption: the header will always fit into the first dblk
        std::memcpy(wptr, (void*)&_txn_hdr, sizeof(txn_hdr_t));
        wr_cnt = sizeof(txn_hdr_t);
        if (size_dblks(rec_size()) > max_size_dblks) // Split required
        {
            std::size_t wsize;
            rem -= sizeof(txn_hdr_t);
            if (rem)
            {
                wsize = rem >= _txn_hdr._xidsize ? _txn_hdr._xidsize : rem;
                std::memcpy((char*)wptr + wr_cnt, _xidp, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            if (rem)
            {
                wsize = rem >= sizeof(_txn_tail) ? sizeof(_txn_tail) : rem;
                std::memcpy((char*)wptr + wr_cnt, (void*)&_txn_tail, wsize);
                wr_cnt += wsize;
                rem -= wsize;
            }
            assert(rem == 0);
        }
        else // No split required
        {
            std::memcpy((char*)wptr + wr_cnt, _xidp, _txn_hdr._xidsize);
            wr_cnt += _txn_hdr._xidsize;
            std::memcpy((char*)wptr + wr_cnt, (void*)&_txn_tail, sizeof(_txn_tail));
            wr_cnt += sizeof(_txn_tail);
#ifdef RHM_CLEAN
            std::size_t dblk_rec_size = size_dblks(rec_size()) * JRNL_DBLK_SIZE;
            std::memset((char*)wptr + wr_cnt, RHM_CLEAN_CHAR, dblk_rec_size - wr_cnt);
#endif
        }
    }
    return size_dblks(wr_cnt);
}

uint32_t
txn_rec::decode(rec_hdr_t& h, void* rptr, uint32_t rec_offs_dblks, uint32_t max_size_dblks)
{
    assert(rptr != 0);
    assert(max_size_dblks > 0);

    std::size_t rd_cnt = 0;
    if (rec_offs_dblks) // Continuation of record on new page
    {
        const uint32_t hdr_xid_dblks = size_dblks(sizeof(txn_hdr_t) + _txn_hdr._xidsize);
        const uint32_t hdr_xid_tail_dblks = size_dblks(sizeof(txn_hdr_t) +  _txn_hdr._xidsize + sizeof(rec_tail_t));
        const std::size_t rec_offs = rec_offs_dblks * JRNL_DBLK_SIZE;

        if (hdr_xid_tail_dblks - rec_offs_dblks <= max_size_dblks)
        {
            // Remainder of xid fits within this page
            if (rec_offs - sizeof(txn_hdr_t) < _txn_hdr._xidsize)
            {
                // Part of xid still outstanding, copy remainder of xid and tail
                const std::size_t xid_offs = rec_offs - sizeof(txn_hdr_t);
                const std::size_t xid_rem = _txn_hdr._xidsize - xid_offs;
                std::memcpy((char*)_buff + xid_offs, rptr, xid_rem);
                rd_cnt = xid_rem;
                std::memcpy((void*)&_txn_tail, ((char*)rptr + rd_cnt), sizeof(_txn_tail));
                chk_tail();
                rd_cnt += sizeof(_txn_tail);
            }
            else
            {
                // Tail or part of tail only outstanding, complete tail
                const std::size_t tail_offs = rec_offs - sizeof(txn_hdr_t) - _txn_hdr._xidsize;
                const std::size_t tail_rem = sizeof(rec_tail_t) - tail_offs;
                std::memcpy((char*)&_txn_tail + tail_offs, rptr, tail_rem);
                chk_tail();
                rd_cnt = tail_rem;
            }
        }
        else if (hdr_xid_dblks - rec_offs_dblks <= max_size_dblks)
        {
            // Remainder of xid fits within this page, tail split
            const std::size_t xid_offs = rec_offs - sizeof(txn_hdr_t);
            const std::size_t xid_rem = _txn_hdr._xidsize - xid_offs;
            std::memcpy((char*)_buff + xid_offs, rptr, xid_rem);
            rd_cnt += xid_rem;
            const std::size_t tail_rem = (max_size_dblks * JRNL_DBLK_SIZE) - rd_cnt;
            if (tail_rem)
            {
                std::memcpy((void*)&_txn_tail, ((char*)rptr + xid_rem), tail_rem);
                rd_cnt += tail_rem;
            }
        }
        else
        {
            // Remainder of xid split
            const std::size_t xid_cp_size = (max_size_dblks * JRNL_DBLK_SIZE);
            std::memcpy((char*)_buff + rec_offs - sizeof(txn_hdr_t), rptr, xid_cp_size);
            rd_cnt += xid_cp_size;
        }
    }
    else // Start of record
    {
        // Get and check header
        //_txn_hdr.hdr_copy(h);
        ::rec_hdr_copy(&_txn_hdr._rhdr, &h);
        rd_cnt = sizeof(rec_hdr_t);
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        rd_cnt += sizeof(uint32_t); // Filler 0
#endif
        _txn_hdr._xidsize = *(std::size_t*)((char*)rptr + rd_cnt);
        rd_cnt = sizeof(txn_hdr_t);
        chk_hdr();
        _buff = std::malloc(_txn_hdr._xidsize);
        MALLOC_CHK(_buff, "_buff", "txn_rec", "decode");
        const uint32_t hdr_xid_dblks = size_dblks(sizeof(txn_hdr_t) + _txn_hdr._xidsize);
        const uint32_t hdr_xid_tail_dblks = size_dblks(sizeof(txn_hdr_t) + _txn_hdr._xidsize +
                sizeof(rec_tail_t));

        // Check if record (header + xid + tail) fits within this page, we can check the
        // tail before the expense of copying data to memory
        if (hdr_xid_tail_dblks <= max_size_dblks)
        {
            // Entire header, xid and tail fits within this page
            std::memcpy(_buff, (char*)rptr + rd_cnt, _txn_hdr._xidsize);
            rd_cnt += _txn_hdr._xidsize;
            std::memcpy((void*)&_txn_tail, (char*)rptr + rd_cnt, sizeof(_txn_tail));
            rd_cnt += sizeof(_txn_tail);
            chk_tail();
        }
        else if (hdr_xid_dblks <= max_size_dblks)
        {
            // Entire header and xid fit within this page, tail split
            std::memcpy(_buff, (char*)rptr + rd_cnt, _txn_hdr._xidsize);
            rd_cnt += _txn_hdr._xidsize;
            const std::size_t tail_rem = (max_size_dblks * JRNL_DBLK_SIZE) - rd_cnt;
            if (tail_rem)
            {
                std::memcpy((void*)&_txn_tail, (char*)rptr + rd_cnt, tail_rem);
                rd_cnt += tail_rem;
            }
        }
        else
        {
            // Header fits within this page, xid split
            const std::size_t xid_cp_size = (max_size_dblks * JRNL_DBLK_SIZE) - rd_cnt;
            std::memcpy(_buff, (char*)rptr + rd_cnt, xid_cp_size);
            rd_cnt += xid_cp_size;
        }
    }
    return size_dblks(rd_cnt);
}

bool
txn_rec::rcv_decode(rec_hdr_t h, std::ifstream* ifsp, std::size_t& rec_offs)
{
    if (rec_offs == 0)
    {
        // Read header, allocate for xid
        //_txn_hdr.hdr_copy(h);
        ::rec_hdr_copy(&_txn_hdr._rhdr, &h);
#if defined(JRNL_BIG_ENDIAN) && defined(JRNL_32_BIT)
        ifsp->ignore(sizeof(uint32_t)); // _filler0
#endif
        ifsp->read((char*)&_txn_hdr._xidsize, sizeof(std::size_t));
#if defined(JRNL_LITTLE_ENDIAN) && defined(JRNL_32_BIT)
        ifsp->ignore(sizeof(uint32_t)); // _filler0
#endif
        rec_offs = sizeof(txn_hdr_t);
        _buff = std::malloc(_txn_hdr._xidsize);
        MALLOC_CHK(_buff, "_buff", "txn_rec", "rcv_decode");
    }
    if (rec_offs < sizeof(txn_hdr_t) + _txn_hdr._xidsize)
    {
        // Read xid (or continue reading xid)
        std::size_t offs = rec_offs - sizeof(txn_hdr_t);
        ifsp->read((char*)_buff + offs, _txn_hdr._xidsize - offs);
        std::size_t size_read = ifsp->gcount();
        rec_offs += size_read;
        if (size_read < _txn_hdr._xidsize - offs)
        {
            assert(ifsp->eof());
            // As we may have read past eof, turn off fail bit
            ifsp->clear(ifsp->rdstate()&(~std::ifstream::failbit));
            assert(!ifsp->fail() && !ifsp->bad());
            return false;
        }
    }
    if (rec_offs < sizeof(txn_hdr_t) + _txn_hdr._xidsize + sizeof(rec_tail_t))
    {
        // Read tail (or continue reading tail)
        std::size_t offs = rec_offs - sizeof(txn_hdr_t) - _txn_hdr._xidsize;
        ifsp->read((char*)&_txn_tail + offs, sizeof(rec_tail_t) - offs);
        std::size_t size_read = ifsp->gcount();
        rec_offs += size_read;
        if (size_read < sizeof(rec_tail_t) - offs)
        {
            assert(ifsp->eof());
            // As we may have read past eof, turn off fail bit
            ifsp->clear(ifsp->rdstate()&(~std::ifstream::failbit));
            assert(!ifsp->fail() && !ifsp->bad());
            return false;
        }
    }
    ifsp->ignore(rec_size_dblks() * JRNL_DBLK_SIZE - rec_size());
    chk_tail(); // Throws if tail invalid or record incomplete
    assert(!ifsp->fail() && !ifsp->bad());
    return true;
}

std::size_t
txn_rec::get_xid(void** const xidpp)
{
    if (!_buff)
    {
        *xidpp = 0;
        return 0;
    }
    *xidpp = _buff;
    return _txn_hdr._xidsize;
}

std::string&
txn_rec::str(std::string& str) const
{
    std::ostringstream oss;
    if (_txn_hdr._rhdr._magic == QLS_TXA_MAGIC)
        oss << "dtxa_rec: m=" << _txn_hdr._rhdr._magic;
    else
        oss << "dtxc_rec: m=" << _txn_hdr._rhdr._magic;
    oss << " v=" << (int)_txn_hdr._rhdr._version;
    oss << " rid=" << _txn_hdr._rhdr._rid;
    oss << " xid=\"" << _xidp << "\"";
    str.append(oss.str());
    return str;
}

std::size_t
txn_rec::xid_size() const
{
    return _txn_hdr._xidsize;
}

std::size_t
txn_rec::rec_size() const
{
    return sizeof(txn_hdr_t) + _txn_hdr._xidsize + sizeof(rec_tail_t);
}

void
txn_rec::chk_hdr() const
{
    jrec::chk_hdr(_txn_hdr._rhdr);
    if (_txn_hdr._rhdr._magic != QLS_TXA_MAGIC && _txn_hdr._rhdr._magic != QLS_TXC_MAGIC)
    {
        std::ostringstream oss;
        oss << std::hex << std::setfill('0');
        oss << "dtx magic: rid=0x" << std::setw(16) << _txn_hdr._rhdr._rid;
        oss << ": expected=(0x" << std::setw(8) << QLS_TXA_MAGIC;
        oss << " or 0x" << QLS_TXC_MAGIC;
        oss << ") read=0x" << std::setw(2) << (int)_txn_hdr._rhdr._magic;
        throw jexception(jerrno::JERR_JREC_BADRECHDR, oss.str(), "txn_rec", "chk_hdr");
    }
}

void
txn_rec::chk_hdr(uint64_t rid) const
{
    chk_hdr();
    jrec::chk_rid(_txn_hdr._rhdr, rid);
}

void
txn_rec::chk_tail() const
{
    jrec::chk_tail(_txn_tail, _txn_hdr._rhdr);
}

void
txn_rec::clean()
{
    // clean up allocated memory here
}

} // namespace journal
} // namespace mrg