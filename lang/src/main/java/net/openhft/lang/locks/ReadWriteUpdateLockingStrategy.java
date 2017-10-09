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

/**
 * Logic of read-write-update lock state transitions.
 *
 * Read lock - could be several at the same time.
 * Update lock - doesn't block reads, but couldn't be several update locks at the same time
 * Write lock - exclusive
 */
public interface ReadWriteUpdateLockingStrategy extends ReadWriteLockingStrategy {

    <T> boolean tryUpdateLock(NativeAtomicAccess<T> access, T t, long offset);

    <T> boolean tryUpgradeReadToUpdateLock(NativeAtomicAccess<T> access, T t, long offset);

    <T> boolean tryUpgradeUpdateToWriteLock(NativeAtomicAccess<T> access, T t, long offset);

    <T> void updateUnlock(NativeAtomicAccess<T> access, T t, long offset);

    <T> void downgradeUpdateToReadLock(NativeAtomicAccess<T> access, T t, long offset);

    <T> void downgradeWriteToUpdateLock(NativeAtomicAccess<T> access, T t, long offset);

    boolean isUpdateLocked(long state);
}
