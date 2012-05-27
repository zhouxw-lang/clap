/*
 * Changed by Xiaowei Zhou for Clap, 2012
 * 
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
package org.apache.felix.framework;

import java.io.IOException;
import java.net.InetAddress;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessControlException;
import java.security.AllPermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.felix.framework.util.FelixConstants;
import org.apache.felix.framework.util.StringMap;
import org.apache.felix.framework.util.Util;
import org.apache.felix.framework.util.manifestparser.ManifestParser;
import org.apache.felix.framework.cache.Content;
import org.apache.felix.framework.util.manifestparser.R4Library;
import org.apache.felix.framework.wiring.BundleCapabilityImpl;
import org.apache.felix.framework.wiring.BundleWireImpl;
import org.osgi.framework.AdminPermission;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

/**
 * The ExtensionManager class is used in several ways.
 * <p>
 * First, a private instance is added (as URL with the instance as
 * URLStreamHandler) to the classloader that loaded the class.
 * It is assumed that this is an instance of URLClassloader (if not extension
 * bundles will not work). Subsequently, extension bundles can be managed by
 * instances of this class (their will be one instance per framework instance).
 * </p>
 * <p>
 * Second, it is used as module definition of the systembundle. Added extension
 * bundles with exported packages will contribute their exports to the
 * systembundle export.
 * </p>
 * <p>
 * Third, it is used as content loader of the systembundle. Added extension
 * bundles exports will be available via this loader.
 * </p>
 */
// The general approach is to have one private static instance that we register
// with the parent classloader and one instance per framework instance that
// keeps track of extension bundles and systembundle exports for that framework
// instance.
//by xiaowei zhou, change to public, 20111104
public class ExtensionManager extends URLStreamHandler implements Content
{
    // The private instance that is added to Felix.class.getClassLoader() -
    // will be null if extension bundles are not supported (i.e., we are not
    // loaded by an instance of URLClassLoader)
    static final ExtensionManager m_extensionManager;

    static
    {
        // pre-init the url sub-system as otherwise we don't work on gnu/classpath
        ExtensionManager extensionManager = new ExtensionManager();
        try
        {
            (new URL("http://felix.extensions:9/")).openConnection();
        }
        catch (Throwable t)
        {
            // This doesn't matter much - we only need the above to init the url subsystem
        }

        // We use the secure action of Felix to add a new instance to the parent
        // classloader.
        try
        {
            Felix.m_secureAction.addURLToURLClassLoader(Felix.m_secureAction.createURL(
                Felix.m_secureAction.createURL(null, "http:", extensionManager),
                "http://felix.extensions:9/", extensionManager),
                Felix.class.getClassLoader());
        }
        catch (Throwable ex)
        {
            // extension bundles will not be supported.
            extensionManager = null;
        }
        m_extensionManager = extensionManager;
    }

    private final Logger m_logger;
    private final Map m_configMap;
    private final Map m_headerMap = new StringMap(false);
    private final BundleRevision m_systemBundleRevision;
    private volatile List<BundleCapability> m_capabilities = Collections.EMPTY_LIST;
    private volatile Set<String> m_exportNames = Collections.EMPTY_SET;
    private volatile Object m_securityContext = null;
    private final List m_extensions;
    private volatile Bundle[] m_extensionsCache;
    private final Set m_names;
    private final Map m_sourceToExtensions;

    // This constructor is only used for the private instance added to the parent
    // classloader.
    private ExtensionManager()
    {
        m_logger = null;
        m_configMap = null;
        m_systemBundleRevision = null;
        m_extensions = new ArrayList();
        m_extensionsCache = new Bundle[0];
        m_names = new HashSet();
        m_sourceToExtensions = new HashMap();
    }

    /**
     * This constructor is used to create one instance per framework instance.
     * The general approach is to have one private static instance that we register
     * with the parent classloader and one instance per framework instance that
     * keeps track of extension bundles and systembundle exports for that framework
     * instance.
     *
     * @param logger the logger to use.
     * @param config the configuration to read properties from.
     * @param systemBundleInfo the info to change if we need to add exports.
     */
    ExtensionManager(Logger logger, Map configMap, Felix felix)
    {
        m_logger = logger;
        m_configMap = configMap;
        m_systemBundleRevision = new ExtensionManagerRevision(felix);
        m_extensions = null;
        m_extensionsCache = null;
        m_names = null;
        m_sourceToExtensions = null;

// TODO: FRAMEWORK - Not all of this stuff really belongs here, probably only exports.
        // Populate system bundle header map.
        m_headerMap.put(FelixConstants.BUNDLE_VERSION,
            m_configMap.get(FelixConstants.FELIX_VERSION_PROPERTY));
        m_headerMap.put(FelixConstants.BUNDLE_SYMBOLICNAME,
            FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME);
        m_headerMap.put(FelixConstants.BUNDLE_NAME, "System Bundle");
        m_headerMap.put(FelixConstants.BUNDLE_DESCRIPTION,
            "This bundle is system specific; it implements various system services.");
        m_headerMap.put(FelixConstants.EXPORT_SERVICE,
            "org.osgi.service.packageadmin.PackageAdmin," +
            "org.osgi.service.startlevel.StartLevel," +
            "org.osgi.service.url.URLHandlers");

        // The system bundle exports framework packages as well as
        // arbitrary user-defined packages from the system class path.
        // We must construct the system bundle's export metadata.
        // Get configuration property that specifies which class path
        // packages should be exported by the system bundle.
        String syspkgs =
            (String) m_configMap.get(FelixConstants.FRAMEWORK_SYSTEMPACKAGES);
        // If no system packages were specified, load our default value.
        syspkgs = (syspkgs == null)
            ? Util.getDefaultProperty(logger, Constants.FRAMEWORK_SYSTEMPACKAGES)
            : syspkgs;
        syspkgs = (syspkgs == null) ? "" : syspkgs;
        // If any extra packages are specified, then append them.
        String pkgextra =
            (String) m_configMap.get(FelixConstants.FRAMEWORK_SYSTEMPACKAGES_EXTRA);
        syspkgs = (pkgextra == null) ? syspkgs : syspkgs + "," + pkgextra;
        m_headerMap.put(FelixConstants.BUNDLE_MANIFESTVERSION, "2");
        m_headerMap.put(FelixConstants.EXPORT_PACKAGE, syspkgs);

        // The system bundle alsp provides framework generic capabilities
        // as well as arbitrary user-defined generic capabilities. We must
        // construct the system bundle's capabilitie metadata. Get the
        // configuration property that specifies which capabilities should
        // be provided by the system bundle.
        String syscaps =
            (String) m_configMap.get(FelixConstants.FRAMEWORK_SYSTEMCAPABILITIES);
        // If no system capabilities were specified, load our default value.
        syscaps = (syscaps == null)
            ? Util.getDefaultProperty(logger, Constants.FRAMEWORK_SYSTEMCAPABILITIES)
            : syscaps;
        syscaps = (syscaps == null) ? "" : syscaps;
        // If any extra capabilities are specified, then append them.
        String capextra =
            (String) m_configMap.get(FelixConstants.FRAMEWORK_SYSTEMCAPABILITIES_EXTRA);
        syscaps = (capextra == null) ? syscaps : syscaps + "," + capextra;
        m_headerMap.put(FelixConstants.PROVIDE_CAPABILITY, syscaps);
        try
        {
            ManifestParser mp = new ManifestParser(
                m_logger, m_configMap, m_systemBundleRevision, m_headerMap);
            List<BundleCapability> caps = aliasSymbolicName(mp.getCapabilities());
            appendCapabilities(caps);
        }
        catch (Exception ex)
        {
            m_capabilities = Collections.EMPTY_LIST;
            m_logger.log(
                Logger.LOG_ERROR,
                "Error parsing system bundle export statement: "
                + syspkgs, ex);
        }
    }

    private static List<BundleCapability> aliasSymbolicName(List<BundleCapability> caps)
    {
        if (caps == null)
        {
            return new ArrayList<BundleCapability>(0);
        }

        List<BundleCapability> aliasCaps = new ArrayList<BundleCapability>(caps);

        for (int capIdx = 0; capIdx < aliasCaps.size(); capIdx++)
        {
            // Get the attributes and search for bundle symbolic name.
            for (Entry<String, Object> entry : aliasCaps.get(capIdx).getAttributes().entrySet())
            {
                // If there is a bundle symbolic name attribute, add the
                // standard alias as a value.
                if (entry.getKey().equalsIgnoreCase(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE))
                {
                    // Make a copy of the attribute array.
                    Map<String, Object> aliasAttrs =
                        new HashMap<String, Object>(aliasCaps.get(capIdx).getAttributes());
                    // Add the aliased value.
                    aliasAttrs.put(
                        Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE,
                        new String[] {
                            (String) entry.getValue(),
                            Constants.SYSTEM_BUNDLE_SYMBOLICNAME});
                    // Create the aliased capability to replace the old capability.
                    aliasCaps.set(capIdx, new BundleCapabilityImpl(
                        caps.get(capIdx).getRevision(),
                        caps.get(capIdx).getNamespace(),
                        caps.get(capIdx).getDirectives(),
                        aliasAttrs));
                    // Continue with the next capability.
                    break;
                }
            }
        }

        return aliasCaps;
    }

    public BundleRevision getRevision()
    {
        return m_systemBundleRevision;
    }

    public Object getSecurityContext()
    {
        return m_securityContext;
    }

    public synchronized void setSecurityContext(Object securityContext)
    {
        m_securityContext = securityContext;
    }

    /**
     * Add an extension bundle. The bundle will be added to the parent classloader
     * and it's exported packages will be added to the module definition
     * exports of this instance. Subsequently, they are available form the
     * instance in it's role as content loader.
     *
     * @param felix the framework instance the given extension bundle comes from.
     * @param bundle the extension bundle to add.
     * @throws BundleException if extension bundles are not supported or this is
     *          not a framework extension.
     * @throws SecurityException if the caller does not have the needed
     *          AdminPermission.EXTENSIONLIFECYCLE and security is enabled.
     * @throws Exception in case something goes wrong.
     */
    synchronized void addExtensionBundle(Felix felix, BundleImpl bundle)
        throws SecurityException, BundleException, Exception
    {
        Object sm = System.getSecurityManager();
        if (sm != null)
        {
                ((SecurityManager) sm).checkPermission(
                    new AdminPermission(bundle, AdminPermission.EXTENSIONLIFECYCLE));
        }

        if (!((BundleProtectionDomain) bundle.getProtectionDomain()).impliesDirect(new AllPermission()))
        {
            throw new SecurityException("Extension Bundles must have AllPermission");
        }

        String directive = ManifestParser.parseExtensionBundleHeader((String)
            ((BundleRevisionImpl) bundle.adapt(BundleRevision.class))
                .getHeaders().get(Constants.FRAGMENT_HOST));

        // We only support classpath extensions (not bootclasspath).
        if (!Constants.EXTENSION_FRAMEWORK.equals(directive))
        {
            throw new BundleException("Unsupported Extension Bundle type: " +
                directive, new UnsupportedOperationException(
                "Unsupported Extension Bundle type!"));
        }

        try
        {
            // Merge the exported packages with the exported packages of the systembundle.
            List<BundleCapability> exports = null;
            try
            {
                exports = ManifestParser.parseExportHeader(
                    m_logger, m_systemBundleRevision,
                    (String) ((BundleRevisionImpl) bundle.adapt(BundleRevision.class))
                        .getHeaders().get(Constants.EXPORT_PACKAGE),
                    m_systemBundleRevision.getSymbolicName(), m_systemBundleRevision.getVersion());
                exports = aliasSymbolicName(exports);
            }
            catch (Exception ex)
            {
                m_logger.log(
                    bundle,
                    Logger.LOG_ERROR,
                    "Error parsing extension bundle export statement: "
                    + ((BundleRevisionImpl) bundle.adapt(BundleRevision.class))
                        .getHeaders().get(Constants.EXPORT_PACKAGE), ex);
                return;
            }

            // Add the bundle as extension if we support extensions
            if (m_extensionManager != null)
            {
                // This needs to be the private instance.
                m_extensionManager.addExtension(felix, bundle);
            }
            else
            {
                // We don't support extensions (i.e., the parent is not an URLClassLoader).
                m_logger.log(bundle, Logger.LOG_WARNING,
                    "Unable to add extension bundle to FrameworkClassLoader - Maybe not an URLClassLoader?");
                throw new UnsupportedOperationException(
                    "Unable to add extension bundle to FrameworkClassLoader - Maybe not an URLClassLoader?");
            }
            appendCapabilities(exports);
        }
        catch (Exception ex)
        {
            throw ex;
        }

        BundleRevisionImpl bri = (BundleRevisionImpl) bundle.adapt(BundleRevision.class);
        List<BundleRequirement> reqs = bri.getDeclaredRequirements(BundleRevision.HOST_NAMESPACE);
        List<BundleCapability> caps = getCapabilities(BundleRevision.HOST_NAMESPACE);
        BundleWire bw = new BundleWireImpl(bri, reqs.get(0), m_systemBundleRevision, caps.get(0));
        bri.resolve(
            new BundleWiringImpl(
                m_logger,
                m_configMap,
                null,
                bri,
                null,
                Collections.singletonList(bw),
                Collections.EMPTY_MAP,
                Collections.EMPTY_MAP));
        felix.getDependencies().addDependent(bw);
        felix.setBundleStateAndNotify(bundle, Bundle.RESOLVED);
    }

    /**
     * This is a Felix specific extension mechanism that allows extension bundles
     * to have activators and be started via this method.
     *
     * @param felix the framework instance the extension bundle is installed in.
     * @param bundle the extension bundle to start if it has a Felix specific activator.
     */
    void startExtensionBundle(Felix felix, BundleImpl bundle)
    {
        String activatorClass = (String)
            ((BundleRevisionImpl) bundle.adapt(BundleRevision.class))
                .getHeaders().get(FelixConstants.FELIX_EXTENSION_ACTIVATOR);

        if (activatorClass != null)
        {
            try
            {
// TODO: SECURITY - Should this consider security?
                BundleActivator activator = (BundleActivator)
                    felix.getClass().getClassLoader().loadClass(
                        activatorClass.trim()).newInstance();

// TODO: EXTENSIONMANAGER - This is kind of hacky, can we improve it?
                felix.m_activatorList.add(activator);

                BundleContext context = felix._getBundleContext();

                bundle.setBundleContext(context);

                if ((felix.getState() == Bundle.ACTIVE) || (felix.getState() == Bundle.STARTING))
                {
                    Felix.m_secureAction.startActivator(activator, context);
                }
            }
            catch (Throwable ex)
            {
                m_logger.log(bundle, Logger.LOG_WARNING,
                    "Unable to start Felix Extension Activator", ex);
            }
        }
    }

    /**
     * Remove all extension registered by the given framework instance. Note, it
     * is not possible to unregister allready loaded classes form those extensions.
     * That is why the spec requires a JVM restart.
     *
     * @param felix the framework instance whose extensions need to be unregistered.
     */
    void removeExtensions(Felix felix)
    {
        if (m_extensionManager != null)
        {
            m_extensionManager._removeExtensions(felix);
        }
    }

    private List<BundleCapability> getCapabilities(String namespace)
    {
        List<BundleCapability> caps = m_capabilities;
        List<BundleCapability> result = caps;
        if (namespace != null)
        {
            result = new ArrayList<BundleCapability>();
            for (BundleCapability cap : caps)
            {
                if (cap.getNamespace().equals(namespace))
                {
                    result.add(cap);
                }
            }
        }
        return result;
    }

    private synchronized void appendCapabilities(List<BundleCapability> caps)
    {
        List<BundleCapability> newCaps =
            new ArrayList<BundleCapability>(m_capabilities.size() + caps.size());
        newCaps.addAll(m_capabilities);
        newCaps.addAll(caps);
        m_capabilities = Collections.unmodifiableList(newCaps);
        m_headerMap.put(Constants.EXPORT_PACKAGE, convertCapabilitiesToHeaders(m_headerMap));
    }

    private String convertCapabilitiesToHeaders(Map headers)
    {
        StringBuffer exportSB = new StringBuffer("");
        Set<String> exportNames = new HashSet<String>();

        List<BundleCapability> caps = m_capabilities;
        for (BundleCapability cap : caps)
        {
            if (cap.getNamespace().equals(BundleRevision.PACKAGE_NAMESPACE))
            {
                // Add a comma separate if there is an existing package.
                if (exportSB.length() > 0)
                {
                    exportSB.append(", ");
                }

                // Append exported package information.
                exportSB.append(cap.getAttributes().get(BundleRevision.PACKAGE_NAMESPACE));
                for (Entry<String, String> entry : cap.getDirectives().entrySet())
                {
                    exportSB.append("; ");
                    exportSB.append(entry.getKey());
                    exportSB.append(":=\"");
                    exportSB.append(entry.getValue());
                    exportSB.append("\"");
                }
                for (Entry<String, Object> entry : cap.getAttributes().entrySet())
                {
                    if (!entry.getKey().equals(BundleRevision.PACKAGE_NAMESPACE)
                        && !entry.getKey().equals(Constants.BUNDLE_SYMBOLICNAME_ATTRIBUTE)
                        && !entry.getKey().equals(Constants.BUNDLE_VERSION_ATTRIBUTE))
                    {
                        exportSB.append("; ");
                        exportSB.append(entry.getKey());
                        exportSB.append("=\"");
                        exportSB.append(entry.getValue());
                        exportSB.append("\"");
                    }
                }

                // Remember exported packages.
                exportNames.add(
                    (String) cap.getAttributes().get(BundleRevision.PACKAGE_NAMESPACE));
            }
        }

        m_exportNames = exportNames;

        return exportSB.toString();
    }

    //
    // Classpath Extension
    //

    /*
     * See whether any registered extension provides the class requested. If not
     * throw an IOException.
     */
    public URLConnection openConnection(URL url) throws IOException
    {
        String path = url.getPath();

        if (path.trim().equals("/"))
        {
            return new URLHandlersBundleURLConnection(url);
        }

        Bundle[] extensions = m_extensionsCache;
        URL result = null;
        for (Bundle extBundle : extensions)
        {
            try
            {
                BundleRevisionImpl bri =
                    (BundleRevisionImpl) extBundle.adapt(BundleRevision.class);
                if (bri != null)
                {
                    result = bri.getResourceLocal(path);
                }
            }
            catch (Exception ex)
            {
                // Maybe the bundle went away, so ignore this exception.
            }
            if (result != null)
            {
                return result.openConnection();
            }
        }

        return new URLConnection(url)
            {
                public void connect() throws IOException
                {
                    throw new IOException("Resource not provided by any extension!");
                }
            };
    }

    protected InetAddress getHostAddress(URL u)
    {
        // the extension URLs do not address real hosts
        return null;
    }

    private synchronized void addExtension(Object source, Bundle extension)
    {
        List sourceExtensions = (List) m_sourceToExtensions.get(source);

        if (sourceExtensions == null)
        {
            sourceExtensions = new ArrayList();
            m_sourceToExtensions.put(source, sourceExtensions);
        }

        sourceExtensions.add(extension);

        _add(extension.getSymbolicName(), extension);
        m_extensionsCache = (Bundle[])
                m_extensions.toArray(new Bundle[m_extensions.size()]);
    }

    private synchronized void _removeExtensions(Object source)
    {
        if (m_sourceToExtensions.remove(source) == null)
        {
            return;
        }

        m_extensions.clear();
        m_names.clear();

        for (Iterator iter = m_sourceToExtensions.values().iterator(); iter.hasNext();)
        {
            List extensions = (List) iter.next();
            for (Iterator extIter = extensions.iterator(); extIter.hasNext();)
            {
                Bundle bundle = (Bundle) extIter.next();
                _add(bundle.getSymbolicName(), bundle);
            }
            m_extensionsCache = (Bundle[])
                m_extensions.toArray(new Bundle[m_extensions.size()]);
        }
    }

    private void _add(String name, Bundle extension)
    {
        if (!m_names.contains(name))
        {
            m_names.add(name);
            m_extensions.add(extension);
        }
    }

    public void close()
    {
        // Do nothing on close, since we have nothing open.
    }

    public Enumeration getEntries()
    {
        return new Enumeration()
        {
            public boolean hasMoreElements()
            {
                return false;
            }

            public Object nextElement() throws NoSuchElementException
            {
                throw new NoSuchElementException();
            }
        };
    }

    public boolean hasEntry(String name) {
        return false;
    }

    public byte[] getEntryAsBytes(String name)
    {
        return null;
    }

    public InputStream getEntryAsStream(String name) throws IOException
    {
        return null;
    }

    public Content getEntryAsContent(String name)
    {
        return null;
    }

    public String getEntryAsNativeLibrary(String name)
    {
        return null;
    }

    public URL getEntryAsURL(String name)
    {
        return null;
    }

    //
    // Utility methods.
    //

    class ExtensionManagerRevision extends BundleRevisionImpl
    {
        private final Version m_version;
        private volatile BundleWiring m_wiring;

        ExtensionManagerRevision(Felix felix)
        {
            super(felix, "0");
            m_version = new Version((String)
                m_configMap.get(FelixConstants.FELIX_VERSION_PROPERTY));
        }

        @Override
        public Map getHeaders()
        {
            synchronized (ExtensionManager.this)
            {
                return m_headerMap;
            }
        }

        @Override
        public List<BundleCapability> getDeclaredCapabilities(String namespace)
        {
            return ExtensionManager.this.getCapabilities(namespace);
        }

        @Override
        public String getSymbolicName()
        {
            return FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME;
        }

        @Override
        public Version getVersion()
        {
            return m_version;
        }

        @Override
        public void close()
        {
            // Nothing needed here.
        }

        @Override
        public Content getContent()
        {
            return ExtensionManager.this;
        }

        @Override
        public URL getEntry(String name)
        {
            // There is no content for the system bundle, so return null.
            return null;
        }

        @Override
        public boolean hasInputStream(int index, String urlPath)
        {
            return (getClass().getClassLoader().getResource(urlPath) != null);
        }

        @Override
        public InputStream getInputStream(int index, String urlPath)
        {
            return getClass().getClassLoader().getResourceAsStream(urlPath);
        }

        @Override
        public URL getLocalURL(int index, String urlPath)
        {
            return getClass().getClassLoader().getResource(urlPath);
        }

        @Override
        public void resolve(BundleWiringImpl wire)
        {
            try
            {
                m_wiring = new ExtensionManagerWiring(
                    m_logger, m_configMap, this);
            }
            catch (Exception ex)
            {
                // This should never happen.
            }
        }

        @Override
        public BundleWiring getWiring()
        {
            return m_wiring;
        }
    }

    class ExtensionManagerWiring extends BundleWiringImpl
    {
        ExtensionManagerWiring(
            Logger logger, Map configMap, BundleRevisionImpl revision)
            throws Exception
        {
            super(logger, configMap, null, revision,
                null, Collections.EMPTY_LIST, null, null);
        }

        @Override
        public ClassLoader getClassLoader()
        {
            return getClass().getClassLoader();
        }

        @Override
        public List<BundleCapability> getCapabilities(String namespace)
        {
            return ExtensionManager.this.getCapabilities(namespace);
        }

        @Override
        public List<R4Library> getNativeLibraries()
        {
            return Collections.EMPTY_LIST;
        }

        @Override
        public Class getClassByDelegation(String name) throws ClassNotFoundException
        {
            Class clazz = null;
            String pkgName = Util.getClassPackage(name);
            if (shouldBootDelegate(pkgName))
            {
                try
                {
                    // Get the appropriate class loader for delegation.
                    ClassLoader bdcl = getBootDelegationClassLoader();
                    clazz = bdcl.loadClass(name);
                    // If this is a java.* package, then always terminate the
                    // search; otherwise, continue to look locally if not found.
                    if (pkgName.startsWith("java.") || (clazz != null))
                    {
                        return clazz;
                    }
                }
                catch (ClassNotFoundException ex)
                {
                    // If this is a java.* package, then always terminate the
                    // search; otherwise, continue to look locally if not found.
                    if (pkgName.startsWith("java."))
                    {
                        throw ex;
                    }
                }
            }
            if (clazz == null)
            {
                if (!m_exportNames.contains(Util.getClassPackage(name)))
                {
                    throw new ClassNotFoundException(name);
                }

                clazz = getClass().getClassLoader().loadClass(name);
            }
            return clazz;
        }

        @Override
        public URL getResourceByDelegation(String name)
        {
            return getClass().getClassLoader().getResource(name);
        }

        @Override
        public Enumeration getResourcesByDelegation(String name)
        {
           try
           {
               return getClass().getClassLoader().getResources(name);
           }
           catch (IOException ex)
           {
               return null;
           }
        }

        @Override
        public void dispose()
        {
            // Nothing needed here.
        }
    }
}
