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

package net.openhft.lang.io.serialization;

/**
 * A BytesMarshaller with a byte code for the class.
 */
public interface CompactBytesMarshaller<E> extends BytesMarshaller<E> {
    byte BYTE_BUFFER_CODE = 'B' & 31;
    byte CLASS_CODE = 'C' & 31;
    byte INT_CODE = 'I' & 31;
    byte LONG_CODE = 'L' & 31;
    byte DOUBLE_CODE = 'D' & 31;
    byte DATE_CODE = 'T' & 31;
    byte STRING_CODE = 'S' & 31;
    byte STRINGZ_MAP_CODE = 'Y' & 31; // compressed string.
    byte STRINGZ_CODE = 'Z' & 31; // compressed string.
    byte LIST_CODE = '[';
    byte SET_CODE = '[' & 31;
    byte MAP_CODE = '{';

    /**
     * @return the code for this marshaller
     */
    byte code();
}
