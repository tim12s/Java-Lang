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

public interface LockingStrategy {

    <T> boolean tryLock(NativeAtomicAccess<T> access, T t, long offset);

    <T> void unlock(NativeAtomicAccess<T> access, T t, long offset);

    <T> void reset(NativeAtomicAccess<T> access, T t, long offset);

    long resetState();

    <T> long getState(NativeAtomicAccess<T> access, T t, long offset);

    boolean isLocked(long state);

    int lockCount(long state);

    String toString(long state);

    int sizeInBytes();
}
