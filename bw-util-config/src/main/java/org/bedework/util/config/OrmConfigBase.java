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

import org.bedework.base.ToString;

import java.util.List;

/** Used by configuration classes that want to save a set of
 * orm properties.
 *
 * @author Mike Douglass
 * @param <T>
 */
public class OrmConfigBase<T extends ConfigBase>
        extends ConfigBase<T> implements OrmConfigI {
  private List<String> ormProperties;

  @Override
  public void setOrmProperties(final List<String> val) {
    ormProperties = val;
  }

  @Override
  @ConfInfo(collectionElementName = "ormProperty")
  public List<String> getOrmProperties() {
    return ormProperties;
  }

  @Override
  public void setHibernateDialect(final String val) {
    setOrmProperty("hibernate.dialect", val);
  }

  @Override
  @ConfInfo(dontSave = true)
  public String getHibernateDialect() {
    return getOrmProperty("hibernate.dialect");
  }

  @Override
  public void addOrmProperty(final String name,
                             final String val) {
    setOrmProperties(addListProperty(getOrmProperties(),
                                     name, val));
  }

  @Override
  @ConfInfo(dontSave = true)
  public String getOrmProperty(final String name) {
    return getProperty(getOrmProperties(), name);
  }

  @Override
  public void removeOrmProperty(final String name) {
    removeProperty(getOrmProperties(), name);
  }

  @Override
  @ConfInfo(dontSave = true)
  public void setOrmProperty(final String name,
                             final String val) {
    setOrmProperties(setListProperty(getOrmProperties(),
                                     name, val));
  }

  @Override
  public void toStringSegment(final ToString ts) {
    super.toStringSegment(ts);

    ts.append("ormProperties", getOrmProperties());
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }
}
