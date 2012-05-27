/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.framework.resolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.apache.felix.framework.wiring.BundleRequirementImpl;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWiring;

class HostBundleRevision implements BundleRevision
{
    private final BundleRevision m_host;
    private final List<BundleRevision> m_fragments;
    private List<BundleCapability> m_cachedCapabilities = null;
    private List<BundleRequirement> m_cachedRequirements = null;

    public HostBundleRevision(BundleRevision host, List<BundleRevision> fragments)
    {
        m_host = host;
        m_fragments = fragments;
    }

    public BundleRevision getHost()
    {
        return m_host;
    }

    public List<BundleRevision> getFragments()
    {
        return m_fragments;
    }

    public String getSymbolicName()
    {
        return m_host.getSymbolicName();
    }

    public Version getVersion()
    {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public List<BundleCapability> getDeclaredCapabilities(String namespace)
    {
        if (m_cachedCapabilities == null)
        {
            List<BundleCapability> caps = new ArrayList<BundleCapability>();

            // Wrap host capabilities.
            for (BundleCapability cap : m_host.getDeclaredCapabilities(null))
            {
                caps.add(new HostedCapability(this, (BundleCapabilityImpl) cap));
            }

            // Wrap fragment capabilities.
            if (m_fragments != null)
            {
                for (BundleRevision fragment : m_fragments)
                {
                    for (BundleCapability cap : fragment.getDeclaredCapabilities(null))
                    {
// TODO: OSGi R4.4 - OSGi R4.4 may introduce an identity capability, if so
//       that will need to be excluded from here.
                        caps.add(new HostedCapability(this, (BundleCapabilityImpl) cap));
                    }
                }
            }
            m_cachedCapabilities = Collections.unmodifiableList(caps);
        }
        return m_cachedCapabilities;
    }

    public List<BundleRequirement> getDeclaredRequirements(String namespace)
    {
        if (m_cachedRequirements == null)
        {
            List<BundleRequirement> reqs = new ArrayList<BundleRequirement>();

            // Wrap host requirements.
            for (BundleRequirement req : m_host.getDeclaredRequirements(null))
            {
                reqs.add(new HostedRequirement(this, (BundleRequirementImpl) req));
            }

            // Wrap fragment requirements.
            if (m_fragments != null)
            {
                for (BundleRevision fragment : m_fragments)
                {
                    for (BundleRequirement req : fragment.getDeclaredRequirements(null))
                    {
                        if (!req.getNamespace().equals(BundleRevision.HOST_NAMESPACE))
                        {
                            reqs.add(new HostedRequirement(this, (BundleRequirementImpl) req));
                        }
                    }
                }
            }
            m_cachedRequirements = Collections.unmodifiableList(reqs);
        }
        return m_cachedRequirements;
    }

    public int getTypes()
    {
        return m_host.getTypes();
    }

    public BundleWiring getWiring()
    {
        return null;
    }

    public Bundle getBundle()
    {
        return m_host.getBundle();
    }

    public String toString()
    {
        return m_host.toString();
    }
}