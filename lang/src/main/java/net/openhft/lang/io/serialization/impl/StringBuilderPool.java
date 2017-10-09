/*
 * Copyright 2016 higherfrequencytrading.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openhft.lang.io.serialization.impl;

/**
 * Created by peter.lawrey on 29/10/14.
 */
public class StringBuilderPool {
    final ThreadLocal<StringBuilder> sbtl = new ThreadLocal<StringBuilder>();

    public StringBuilder acquireStringBuilder() {
        StringBuilder sb = sbtl.get();
        if (sb == null) {
            sbtl.set(sb = new StringBuilder(128));
        }
        sb.setLength(0);
        return sb;
    }
}
