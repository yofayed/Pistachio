/*
 * Copyright 2014 Yahoo! Inc. Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law or
 * agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and
 * limitations under the License. See accompanying LICENSE file.
 */


package com.yahoo.ads.pb;
import java.util.List;
import com.yahoo.ads.pb.exception.*;

public interface PistachiosClientImpl {
	public byte[] lookup(long id) throws MasterNotFoundException, Exception;
	public boolean store(long id, byte[] value) throws MasterNotFoundException, ConnectionBrokenException;
    public boolean processBatch(long id, List<byte[]> events) throws MasterNotFoundException, ConnectionBrokenException;
    public void close();
}
