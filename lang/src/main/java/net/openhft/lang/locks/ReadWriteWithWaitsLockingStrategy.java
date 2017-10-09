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

package net.openhft.lang.locks;

public interface ReadWriteWithWaitsLockingStrategy extends ReadWriteLockingStrategy {

    <T> void registerWait(NativeAtomicAccess<T> access, T t, long offset);

    <T> void deregisterWait(NativeAtomicAccess<T> access, T t, long offset);

    <T> boolean tryWriteLockAndDeregisterWait(NativeAtomicAccess<T> access, T t, long offset);

    <T> boolean tryUpgradeReadToWriteLockAndDeregisterWait(
            NativeAtomicAccess<T> access, T t, long offset);

    <T> void resetKeepingWaits(NativeAtomicAccess<T> access, T t, long offset);

    int waitCount(long state);
}
