/*
 * Copyright (C) 2012 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.maven_manipulator.core.fixture;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import org.commonjava.maven.galley.TransferException;
import org.commonjava.maven.galley.event.EventMetadata;
import org.commonjava.maven.galley.model.ConcreteResource;
import org.commonjava.maven.galley.model.Location;
import org.commonjava.maven.galley.model.Transfer;
import org.commonjava.maven.galley.model.TransferOperation;
import org.commonjava.maven.galley.spi.transport.DownloadJob;
import org.commonjava.maven.galley.spi.transport.ExistenceJob;
import org.commonjava.maven.galley.spi.transport.ListingJob;
import org.commonjava.maven.galley.spi.transport.PublishJob;
import org.commonjava.maven.galley.spi.transport.Transport;
import org.jboss.pnc.maven_manipulator.io.resolver.MavenLocationExpander;

public class StubTransport
        implements Transport {

    private final Map<String, byte[]> dataMap;

    public StubTransport(final Map<String, byte[]> dataMap) {
        this.dataMap = dataMap;
    }

    @Override
    public DownloadJob createDownloadJob(
            final ConcreteResource resource,
            final Transfer transfer,
            Map<Transfer, Long> var3,
            final int timeoutSeconds,
            EventMetadata eventMetadata) {
        System.out.println("Creating download for: " + resource.getPath());
        return new DownloadJob() {
            Transfer t = null;

            @Override
            public long getTransferSize() {
                return 0;
            }

            @Override
            public Transfer getTransfer() {
                return t;
            }

            @Override
            public DownloadJob call()
                    throws Exception {
                final byte[] data = dataMap.get(resource.getPath());

                if (data == null) {
                    return null;
                }

                t = transfer;
                t.delete(false);

                try (OutputStream out = t.openOutputStream(TransferOperation.DOWNLOAD)) {
                    out.write(data);
                }
                return this;
            }

            @Override
            public TransferException getError() {
                return null;
            }
        };
    }

    @Override
    public ExistenceJob createExistenceJob(final ConcreteResource arg0, final Transfer transfer, final int arg1)
            throws TransferException {
        throw new TransferException("Not implemented in this stub!");
    }

    @Override
    public PublishJob createPublishJob(
            final ConcreteResource arg0,
            final InputStream arg1,
            final long arg2,
            final int arg3)
            throws TransferException {
        throw new TransferException("Not implemented in this stub!");
    }

    @Override
    public PublishJob createPublishJob(
            final ConcreteResource arg0,
            final InputStream arg1,
            final long arg2,
            final String arg3,
            final int arg4)
            throws TransferException {
        throw new TransferException("Not implemented in this stub!");
    }

    @Override
    public boolean handles(final Location loc) {
        return loc.equals(MavenLocationExpander.EXPANSION_TARGET);
    }

    @Override
    public boolean allowsCaching() {
        return false;
    }

    @Override
    public ListingJob createListingJob(final ConcreteResource resource, final Transfer target, final int timeoutSeconds)
            throws TransferException {
        throw new TransferException("Not implemented in this stub!");
    }
}
