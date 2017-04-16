/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kylin.dict;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import com.google.common.util.concurrent.MoreExecutors;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.lock.DistributedJobLock;
import org.apache.kylin.common.util.ClassUtil;
import org.apache.kylin.common.util.Dictionary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GlobalDictinary based on whole cube, to ensure one value has same dict id in different segments.
 * GlobalDictinary mainly used for count distinct measure to support rollup among segments.
 * Created by sunyerui on 16/5/24.
 */
public class GlobalDictionaryBuilder implements IDictionaryBuilder {
    private AppendTrieDictionaryBuilder builder;
    private int baseId;

    private DistributedJobLock lock;
    private String sourceColumn;
    //the job thread name is UUID+threadID
    private final String jobUUID = Thread.currentThread().getName();
    private int counter;

    private static Logger logger = LoggerFactory.getLogger(GlobalDictionaryBuilder.class);

    @Override
    public void init(DictionaryInfo dictInfo, int baseId) throws IOException {
        if (dictInfo == null) {
            throw new IllegalArgumentException("GlobalDictinaryBuilder must used with an existing DictionaryInfo");
        }

        sourceColumn = dictInfo.getSourceTable() + "_" + dictInfo.getSourceColumn();
        lock(sourceColumn);

        int maxEntriesPerSlice = KylinConfig.getInstanceFromEnv().getAppendDictEntrySize();
        this.builder = new AppendTrieDictionaryBuilder(dictInfo.getResourceDir(), maxEntriesPerSlice);
        this.baseId = baseId;
    }

    @Override
    public boolean addValue(String value) {
        if (++counter % 1_000_000 == 0) {
            if (lock.lockWithClient(getLockPath(sourceColumn), jobUUID)) {
                logger.info("processed {} values", counter);
            } else {
                throw new RuntimeException("Failed to create global dictionary on " + sourceColumn + " This client doesn't keep the lock");
            }
        }

        if (value == null) {
            return false;
        }

        try {
            builder.addValue(value);
        } catch (Throwable e) {
            checkAndUnlock();
            throw new RuntimeException(String.format("Failed to create global dictionary on %s ", sourceColumn), e);
        }

        return true;
    }

    @Override
    public Dictionary<String> build() throws IOException {
        try {
            if (lock.lockWithClient(getLockPath(sourceColumn), jobUUID)) {
                return builder.build(baseId);
            }
        } finally {
            checkAndUnlock();
        }
        return new AppendTrieDictionary<>();
    }

    private void lock(final String sourceColumn) throws IOException {
        lock = (DistributedJobLock) ClassUtil.newInstance("org.apache.kylin.storage.hbase.util.ZookeeperDistributedJobLock");

        if (!lock.lockWithClient(getLockPath(sourceColumn), jobUUID)) {
            logger.info("{} will wait the lock for {} ", jobUUID, sourceColumn);

            final BlockingQueue<String> bq = new ArrayBlockingQueue<String>(1);

            PathChildrenCache cache = lock.watch(getWatchPath(sourceColumn), MoreExecutors.sameThreadExecutor(), new DistributedJobLock.WatcherProcess() {
                @Override
                public void process(String path, String data) {
                    if (!data.equalsIgnoreCase(jobUUID) && lock.lockWithClient(getLockPath(sourceColumn), jobUUID)) {
                        try {
                            bq.put("getLock");
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            });

            long start = System.currentTimeMillis();

            try {
                bq.take();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                cache.close();
            }

            logger.info("{} has waited the lock {} ms for {} ", jobUUID, (System.currentTimeMillis() - start), sourceColumn);
        }
    }

    private void checkAndUnlock() {
        if (lock.lockWithClient(getLockPath(sourceColumn), jobUUID)) {
            lock.unlock(getLockPath(sourceColumn));
        }
    }

    private static final String GLOBAL_DICT_LOCK_PATH = "/kylin/dict/lock";

    private String getLockPath(String pathName) {
        return GLOBAL_DICT_LOCK_PATH + "/" + KylinConfig.getInstanceFromEnv().getMetadataUrlPrefix() + "/" + pathName + "/lock";
    }

    private String getWatchPath(String pathName) {
        return GLOBAL_DICT_LOCK_PATH + "/" + KylinConfig.getInstanceFromEnv().getMetadataUrlPrefix() + "/" + pathName;
    }
}
