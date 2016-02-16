/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kylin.common;

/**
 * Current version is: 2.1
 *
 * @since 2.1
 */
public class KylinVersion {
    /**
     * Require MANUAL updating kylin version per ANY upgrading.
     */
    private static final String CURRENT_KYLIN_VERSION = "2.1";

    /**
     * Get current Kylin version
     *
     * Currently the implementation is reading directly from constant variable
     *
     * @return current kylin version in String
     */
    public static String getCurrentVersion(){
        return CURRENT_KYLIN_VERSION;
    }
}