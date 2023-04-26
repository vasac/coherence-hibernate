/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at
 * https://oss.oracle.com/licenses/upl.
 */
package com.oracle.coherence.hibernate.cache.v6.access;

import com.oracle.coherence.hibernate.cache.v6.access.processor.AfterInsertProcessor;
import com.oracle.coherence.hibernate.cache.v6.access.processor.AfterUpdateProcessor;
import com.oracle.coherence.hibernate.cache.v6.access.processor.SoftLockItemProcessor;
import com.oracle.coherence.hibernate.cache.v6.access.processor.SoftUnlockItemProcessor;
import com.oracle.coherence.hibernate.cache.v6.region.CoherenceRegionValue;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.cache.spi.support.DomainDataStorageAccess;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import com.oracle.coherence.hibernate.cache.v6.access.processor.GetProcessor;
import com.oracle.coherence.hibernate.cache.v6.access.processor.ReadWritePutFromLoadProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;

/**
 * A AbstractReadWriteCoherenceEntityDataAccess is AbstractCoherenceEntityDataAccess
 * implementing the "read-write" cache concurrency strategy as defined by Hibernate.
 *
 * @author Randy Stafford
 * @author Gunnar Hillert
 */
abstract class AbstractReadWriteCoherenceEntityDataAccess
extends AbstractCoherenceEntityDataAccess
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractReadWriteCoherenceEntityDataAccess.class);

    // ---- Constructors

    /**
     * Complete constructor.
     *
     * @param domainDataRegion the CoherenceRegion for this AbstractReadWriteCoherenceEntityDataAccess
     * @param domainDataStorageAccess the Hibernate SessionFactoryOptions object
     * @param versionComparator
     */
    public AbstractReadWriteCoherenceEntityDataAccess(DomainDataRegion domainDataRegion,
            DomainDataStorageAccess domainDataStorageAccess, Comparator<?> versionComparator)
    {
        super(domainDataRegion, domainDataStorageAccess, versionComparator);
    }


    // ---- interface org.hibernate.cache.spi.access.RegionAccessStrategy

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(SharedSessionContractImplementor session, Object key) throws CacheException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("get({})", key);
        }
        return getCoherenceRegion().invoke(key, new GetProcessor());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.hibernate.cache.spi.access.SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) throws CacheException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("lockItem({}, {})", key, version);
        }
        CoherenceRegionValue valueIfAbsent = newCacheValue(null, version);
        CoherenceRegionValue.SoftLock newSoftLock = newSoftLock();
        SoftLockItemProcessor processor = new SoftLockItemProcessor(valueIfAbsent, newSoftLock);
        getCoherenceRegion().invoke(key, processor);
        return newSoftLock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object version, boolean minimalPutOverride)
    throws CacheException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("putFromLoad({}, {}, {}, {})", key, value, version, minimalPutOverride);
        }
        CoherenceRegionValue newCacheValue = newCacheValue(value, version);
        ReadWritePutFromLoadProcessor processor = new ReadWritePutFromLoadProcessor(minimalPutOverride, this.getCoherenceRegion().nextTimestamp(), newCacheValue, super.getVersionComparator());
        return (Boolean) getCoherenceRegion().invoke(key, processor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unlockItem(SharedSessionContractImplementor session, Object key, org.hibernate.cache.spi.access.SoftLock lock) throws CacheException
    {
        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("unlockItem({}, {})", key, lock);
        }
        SoftUnlockItemProcessor processor = new SoftUnlockItemProcessor(lock, getCoherenceRegion().nextTimestamp());
        getCoherenceRegion().invoke(key, processor);
    }


    // ---- Subclass interface

    /**
     * Coherence-based implementation of behavior common to:
     * 1. org.hibernate.cache.spi.access.EntityRegionAccessStrategy.afterInsert(Object key, Object value, Object version) and
     * 2. org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy.afterInsert(Object key, Object value).
     *
     * The only difference in implementation is that the cache value in a NaturalIdRegion will have a null version object.
     *
     * @param key the key at which to insert a value
     * @param value the value to insert
     *
     * @return a boolean indicating whether cache contents were modified
     */
    protected boolean afterInsert(Object key, CoherenceRegionValue value)
    {
        final AfterInsertProcessor afterInsertProcessor = new AfterInsertProcessor(value);
        return (Boolean) getCoherenceRegion().invoke(key, afterInsertProcessor);
    }

    /**
     * Coherence-based implementation of behavior common to:
     * 1. org.hibernate.cache.spi.access.EntityRegionAccessStrategy.afterUpdate(Object key, Object value, Object currentVersion, Object previousVersion, SoftLock lock) and
     * 2. org.hibernate.cache.spi.access.NaturalIdRegionAccessStrategy.afterUpdate(Object key, Object value, SoftLock lock).
     *
     * The only difference in implementation is that the cache value in a NaturalIdRegion will have a null version object.
     *
     * @param key the key at which to insert a value
     * @param value the value to insert
     * @param softLock the softLock acquired in an earlier lockItem call with the argument key
     *
     * @return a boolean indicating whether cache contents were modified
     */
    protected boolean afterUpdate(Object key, CoherenceRegionValue value, SoftLock softLock)
    {
        long timeOfSoftLockRelease = getCoherenceRegion().nextTimestamp();
        AfterUpdateProcessor afterUpdateProcessor = new AfterUpdateProcessor(value, softLock, timeOfSoftLockRelease);
        return (Boolean) getCoherenceRegion().invoke(key, afterUpdateProcessor);
    }


    // ---- Internal: factory

    /**
     * Returns a new SoftLock.
     *
     * @return a SoftLock newly constructed
     */
    private CoherenceRegionValue.SoftLock newSoftLock()
    {
        long lockExpirationTime = getCoherenceRegion().newSoftLockExpirationTime();
        return new CoherenceRegionValue.SoftLock(getUuid(), nextSoftLockSequenceNumber(), lockExpirationTime);
    }

}
