/* ********************************************************************
    Licensed to Jasig under one or more contributor license
    agreements. See the NOTICE file distributed with this work
    for additional information regarding copyright ownership.
    Jasig licenses this file to you under the Apache License,
    Version 2.0 (the "License"); you may not use this file
    except in compliance with the License. You may obtain a
    copy of the License at:

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on
    an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied. See the License for the
    specific language governing permissions and limitations
    under the License.
*/
package org.bedework.util.jmx;

import org.bedework.util.config.ConfigBase;
import org.bedework.util.config.ConfigException;
import org.bedework.util.config.ConfigurationFileStore;
import org.bedework.util.config.ConfigurationStore;
import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/** A configuration has a directory and name.
 *
 * <p>The base directory in which all configs are found is
 * specified by the system property "org.bedework.config.dir".
 *
 * <p>Each may be augmented by providing a path suffix - used to add additional
 * path elements to the base path.
 *
 * @author douglm
 * @param <T>
 *
 */
public abstract class ConfBase<T extends ConfigBase>
        implements Logged, ConfBaseMBean {
  public static final String statusDone = "Done";
  public static final String statusFailed = "Failed";
  public static final String statusRunning = "Running";
  public static final String statusStopped = "Stopped";
  public static final String statusTimedout = "Timedout";
  public static final String statusInterrupted = "Interrupted";
  public static final String statusUnknown = "Unknown";

  protected T cfg;

  private final String configName;

  private String status = statusUnknown;

  private final static String confDirPname = "org.bedework.config.dir";

  /* From the pfile */
  private static String configBase;
  private static boolean configBaseIsFile;
  private static boolean configBaseIsHttp;

  private static final List<String> httpSchemes;

  static {
    List<String> hs = new ArrayList<>();

    hs.add("http");
    hs.add("https");

    httpSchemes = Collections.unmodifiableList(hs);
  }

  /* The property which defines the path - possibly relative */
  private final String configDirectory;

  private final String pathSuffix;

  private static Set<ObjectName> registeredMBeans = new CopyOnWriteArraySet<ObjectName>();

  private static ManagementContext managementContext;

  private final String serviceName;

  private ConfigurationStore store;

  /**
   *
   * @param serviceName service name e.g. "org.bedework.timezones:service=Convert"
   * @param store
   * @param configName configuration name
   */
  protected ConfBase(final String serviceName,
                     final ConfigurationStore store,
                     final String configName) {
    this.serviceName = serviceName;
    this.configName = configName;
    configDirectory = null;
    pathSuffix = null;
    this.store = store;
  }

  /**
   *
   * @param serviceName service name e.g. "org.bedework.timezones:service=Convert"
   * @param configDirectory Name of config directory
   * @param configName configuration name
   */
  protected ConfBase(final String serviceName,
                     final String configDirectory,
                     final String configName) {
    this(serviceName, configDirectory, null, configName);
  }

  /**
   *
   * @param serviceName service name e.g. "org.bedework.timezones:service=Convert"
   * @param configDirectory Name of config directory
   * @param pathSuffix Specify a suffix to the path to the configuration directory.
   * @param configName configuration name
   */
  protected ConfBase(final String serviceName,
                     final String configDirectory,
                     final String pathSuffix,
                     final String configName) {
    this.serviceName = serviceName;
    this.configDirectory = configDirectory;
    this.configName = configName;
    this.pathSuffix = pathSuffix;
  }

  @Override
  public String getServiceName() {
    return serviceName;
  }

  /**
   * @param val a status.
   */
  public void setStatus(final String val) {
    status = val;
  }

  @Override
  public String getStatus() {
    return status;
  }

  @Override
  public void start() {
  }

  @Override
  public void stop() {
  }

  @Override
  public boolean isRunning() {
    return true;
  }

  /**
   * @return String name of system property
   */
  public String getConfigDirectory() {
    return configDirectory;
  }

  /**
   * @return String path suffix to configs
   */
  public String getPathSuffix() {
    return pathSuffix;
  }

  /** Set a ConfigurationStore
   *
   * @param val
   */
  public void setStore(final ConfigurationStore val) {
    store = val;
  }

  /** Get a ConfigurationStore based on the uri or property value.
   *
   * @return store
   * @throws ConfigException
   */
  public ConfigurationStore getStore() throws ConfigException {
    if (store != null) {
      return store;
    }

    final String cdir = getConfigDirectory();

    final var configPath = getPfilePath(cdir);

    store = new ConfigurationFileStore(configPath);
    return store;
  }

  /**
   * @return the object we are managing
   */
  public T getConfig() {
    return cfg;
  }

  /** (Re)load the configuration
   *
   * @return status
   */
  @MBeanInfo("(Re)load the configuration")
  public abstract String loadConfig();

  protected Set<ObjectName> getRegisteredMBeans() {
    return registeredMBeans;
  }

  /* ========================================================================
   * Attributes
   * ======================================================================== */

  @Override
  public String getConfigName() {
    return configName;
  }

  /* ========================================================================
   * Operations
   * ======================================================================== */

  @Override
  public String saveConfig() {
    try {
      final T config = getConfig();
      if (config == null) {
        return "No configuration to save";
      }

      final ConfigurationStore cs = getStore();

      config.setName(configName);

      cs.saveConfiguration(config);

      return "saved";
    } catch (Throwable t) {
      error(t);
      return t.getLocalizedMessage();
    }
  }

  public static Path ensureDir(final Path path) throws ConfigException {
    final File f = path.toFile();
    if (!f.exists()) {
      throw new ConfigException("No configuration directory at " +
                                        f.getAbsolutePath());
    }

    if (!f.isDirectory()) {
      throw new ConfigException(f.getAbsolutePath() +
                                        " is not a directory");
    }

    return path;
  }

  /* ====================================================================
   *                   Private methods
   * ==================================================================== */

  private Path getPfilePath(final String configDirName)
          throws ConfigException {
    if (configDirName == null) {
      throw new ConfigException("Must supply a config directory name");
    }

    final String baseConfigPath = System.getProperty(confDirPname);

    if (baseConfigPath == null) {
      throw new ConfigException("No system property with name \"" +
                                        confDirPname + "\"");
    }

    final Path path = Paths.get(baseConfigPath);
    ensureDir(path);

    final Path configPath = path.resolve(configDirName);

    if (getPathSuffix() == null) {
      return ensureDir(configPath);
    }

    return ensureDir(configPath.resolve(getPathSuffix()));
  }

  /* ====================================================================
   *                   JMX methods
   * ==================================================================== */

  /* */
  private ObjectName serviceObjectName;

  protected void register(final String serviceType,
                       final String name,
                       final Object view) {
    try {
      ObjectName objectName = createObjectName(serviceType, name);
      register(objectName, view);
    } catch (Throwable t) {
      error("Failed to register " + serviceType + ":" + name);
      error(t);
    }
  }

  protected void unregister(final String serviceType,
                            final String name) {
    try {
      ObjectName objectName = createObjectName(serviceType, name);
      unregister(objectName);
    } catch (Throwable t) {
      error("Failed to unregister " + serviceType + ":" + name);
      error(t);
    }
  }

  protected ObjectName getServiceObjectName() throws MalformedObjectNameException {
    if (serviceObjectName == null) {
      serviceObjectName = new ObjectName(getServiceName());
    }

    return serviceObjectName;
  }

  protected ObjectName createObjectName(final String serviceType,
                                        final String name) throws MalformedObjectNameException {
    // Build the object name for the bean
    Map props = getServiceObjectName().getKeyPropertyList();
    ObjectName objectName = new ObjectName(getServiceObjectName().getDomain() + ":" +
        "service=" + props.get("service") + "," +
        "Type=" + ManagementContext.encodeObjectNamePart(serviceType) + "," +
        "Name=" + ManagementContext.encodeObjectNamePart(name));
    return objectName;
  }

  /* ====================================================================
   *                   Protected methods
   * ==================================================================== */

  /**
   * @return config identified by current config name
   */
  protected T getConfigInfo(final Class<T> cl)throws ConfigException  {
    return getConfigInfo(getStore(), getConfigName(), cl);
  }

  /**
   * @return current state of config
   */
  protected T getConfigInfo(final String configName,
                            final Class<T> cl)throws ConfigException  {
    return getConfigInfo(getStore(), configName, cl);
  }

  @SuppressWarnings("unchecked")
  protected T getConfigInfo(final ConfigurationStore cfs,
                            final String configName,
                            final Class<T> cl) throws ConfigException  {
    try {
      /* Try to load it */

      return (T)cfs.getConfig(configName, cl);
    } catch (ConfigException cfe) {
      throw cfe;
    } catch (Throwable t) {
      throw new ConfigException(t);
    }
  }

  /**
   * @param cl
   * @return config identified by current config name
   */
  protected String loadConfig(final Class<T> cl) {
    try {
      /* Load up the config */

      cfg = getConfigInfo(cl);

      if (cfg == null) {
        return "Unable to read configuration";
      }

      return "OK";
    } catch (Throwable t) {
      error("Failed to load configuration: " + t.getLocalizedMessage());
      error(t);
      return "failed";
    }
  }

  /**
   * @param key
   * @param bean
   * @throws Exception
   */
  protected void register(final ObjectName key,
                          final Object bean) throws Exception {
    try {
      AnnotatedMBean.registerMBean(getManagementContext(), bean, key);
      getRegisteredMBeans().add(key);
    } catch (Throwable e) {
      warn("Failed to register MBean: " + key + ": " + e.getLocalizedMessage());
      if (debug()) {
        error(e);
      }
    }
  }

  /**
   * @param key
   * @throws Exception
   */
  protected void unregister(final ObjectName key) throws Exception {
    if (getRegisteredMBeans().remove(key)) {
      try {
        getManagementContext().unregisterMBean(key);
      } catch (Throwable e) {
        warn("Failed to unregister MBean: " + key);
        if (debug()) {
          error(e);
        }
      }
    }
  }

  /**
   * @return the management context.
   */
  public static ManagementContext getManagementContext() {
    if (managementContext == null) {
      /* Try to find the jboss mbean server * /

      MBeanServer mbsvr = null;

      for (MBeanServer svr: MBeanServerFactory.findMBeanServer(null)) {
        if (svr.getDefaultDomain().equals("jboss")) {
          mbsvr = svr;
          break;
        }
      }

      if (mbsvr == null) {
        Logger.getLogger(ConfBase.class).warn("Unable to locate jboss mbean server");
      }
      managementContext = new ManagementContext(mbsvr);
      */
      managementContext = new ManagementContext(ManagementContext.DEFAULT_DOMAIN);
    }
    return managementContext;
  }
  /**
   *
   * @param serviceName service name e.g. "org.bedework.timezones:service=Convert"
   * @param store
   * @param configName String configuration name
   */
  protected static Object makeObject(final String className,
                                     final String serviceName,
                                     final ConfigurationStore store,
                                     final String configName) {
    try {
      final var objClass = Thread.currentThread()
                                 .getContextClassLoader()
                                 .loadClass(className);

      return objClass.getDeclaredConstructor(String.class,
                                             ConfigurationStore.class,
                                             String.class).
                     newInstance(serviceName, store, configName);
    } catch (final Throwable t) {
      new BwLogger().setLoggedClass(ConfBase.class)
                    .error("Unable to make object ", t);
      return null;
    }
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
