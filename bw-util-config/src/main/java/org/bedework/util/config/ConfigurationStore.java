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
package org.bedework.util.config;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;

/** A configuration store holds configurations, each identified by a unique name,
 *
 * <p>In addition it may contain sub-stores.
 *
 * <p>Not surprisingly this looks like a file system with one file per config
 * and directories representing the stores.
 *
 * <p>A store may be read-only or read-write. If read-write it may require
 * credentials.
 *
 * @author Mike Douglass douglm
 */
public interface ConfigurationStore {
  /**
   * @return true for a read-only store.
   */
  boolean readOnly();

  /**
   * @return path for this store
   */
  Path getDirPath();

  /**
   * @param config to save
   * @throws ConfigException on error
   */
  void saveConfiguration(ConfigBase<?> config) throws ConfigException;

  /** Stored config must indicate class of object as an attribute
   *
   * @param name of the object
   * @return config or null
   * @throws ConfigException on error
   */
  ConfigBase<?> getConfig(String name) throws ConfigException;

  /**
   * @param name of the object
   * @param cl - class of config object
   * @return config or null
   * @throws ConfigException on error
   */
  ConfigBase<?> getConfig(String name,
                          Class<?> cl) throws ConfigException;

  /** List the configurations in the store
   *
   * @return list of configurations
   * @throws ConfigException on error
   */
  List<String> getConfigs() throws ConfigException;

  /** Get the named child store. Create it if it does not exist
   *
   * @param name of the store
   * @return store
   * @throws ConfigException on error
   */
  ConfigurationStore getStore(String name) throws ConfigException;

  /**
   * @param name of bundle
   * @param locale null for default or a locale String e.g. en_US
   * @return a ResourceBundle object or null.
   * @throws ConfigException on error
   */
  ResourceBundle getResource(String name,
                             String locale) throws ConfigException;


  default Path ensureDir(final Path path) throws ConfigException {
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

}
