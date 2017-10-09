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

import net.openhft.lang.io.Bytes;
import net.openhft.lang.model.constraints.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public enum JDKObjectSerializer implements ObjectSerializer {
    INSTANCE;

    @Override
    public void writeSerializable(Bytes bytes, Object object, Class expectedClass) throws IOException {
        ObjectOutputStream oos= new ObjectOutputStream(bytes.outputStream());
        oos.writeObject(object);
        oos.close();
    }

    @Override
    public <T> T readSerializable(@NotNull Bytes bytes, Class<T> expectedClass, T object) throws IOException, ClassNotFoundException {
        ObjectInputStream ois = new ObjectInputStream(bytes.inputStream());
        T obj = (T) ois.readObject();
        ois.close();
        return obj;
    }
}
