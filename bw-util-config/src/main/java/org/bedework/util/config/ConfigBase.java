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

import org.bedework.util.logging.BwLogger;
import org.bedework.util.logging.Logged;
import org.bedework.base.ToString;
import org.bedework.util.misc.Util;
import org.bedework.util.xml.XmlEmit;
import org.bedework.util.xml.XmlEmit.NameSpace;
import org.bedework.util.xml.XmlUtil;
import org.bedework.util.xml.tagdefs.BedeworkServerTags;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/** This class is used as a basis for configuration of system modules. The
 * classes extending this MUST be annotated appropriately with ConfInfo and
 * require setters and getters for all configuration fields.
 *
 * <p>The class itself requires an element name annotation<br/>
 * <code>
 *   &#64;ConfInfo(elementName="example-conf",
 *                 type="defining-class")
 * </code>
 *
 * <p>The type parameter is optional. If not specified the annotated
 * class is the defining class otherwise it is the named class or
 * interface</p>
 *
 * <p>The defining class specifies the getters and setters for the
 * configuration fields. The actual class may have other getters and
 * setters but these will not be dumped or restored.</p>
 *
 * <p>Collection fields MUST be either Set or List and the element class MUST
 * be specified. For example:<br/>
 * <code>
 *     private List<String> props;
 * </code>
 *
 * <p>Also collection getters require a name for the collection element<br/>
 * <code>
 *    &#64;ConfInfo(collectionElementName = "prop")<br/>
 *    public List<String> getProps() {
 * </code>
 *
 * <p>The ConfInfo annotation
 * <code>
 *    &#64;ConfInfo(dontSave = true)
 * </code>
 * allows for getters and setters of values used internally only.
 *
 * <p>The dumped XML will have start elements with a <em>type</em>
 * attribute. The value of that attribute is the <em>actual</em> class
 * being dumped/restored.</p>
 *
 * <p>An example:</p>
 * <pre>
 *   public interface MyConf extends Serializable {
 *     ...
 *     void setMaxLength(final Integer val);
 *     ...
 *    &#64;MBeanInfo("Max length")
 *     Integer getLength();
 *     ...
 *   }
 *
 *   &#64;ConfInfo(elementName = "my-conf",
 *           type = "my.package.MyConf")
 *  public class MyConfImpl extends ConfigBase<MyConfImpl>
 *        implements MyConf {
 *    ...
 *  }
 * </pre>
 *
 * @author Mike Douglass
 * @param <T>
 */
public class ConfigBase<T extends ConfigBase>
        implements Logged, Comparable<T>, Serializable {
  /** The default namespace for the XML elements.
   *
   */
  public final static String ns = BedeworkServerTags.bedeworkSystemNamespace;

  private String name;

  private long lastChanged;

  /**
   * @param val the name
   */
  public void setName(final String val) {
    name = val;
  }

  /** Name for the configuration.
   *
   * @return String
   */
  public String getName() {
    return name;
  }

  public void markChanged() {
    lastChanged = System.currentTimeMillis();
  }

  public long getLastChanged() {
    return lastChanged;
  }

  /** Add our stuff to the StringBuilder
   *
   * @param ts    ToString for result
   */
  public void toStringSegment(final ToString ts) {
    ts.append("name", getName());
    ts.append("lastChanged", getLastChanged());
  }

  /* ====================================================================
   *                   Object methods
   * ==================================================================== */

  @Override
  public int compareTo(final ConfigBase that) {
    return getName().compareTo(that.getName());
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }

  @Override
  public String toString() {
    final ToString ts = new ToString(this);

    toStringSegment(ts);

    return ts.toString();
  }

  /* ====================================================================
   *                   Property methods
   * ==================================================================== */

  /**
   * @param list
   * @param name of property
   * @param val
   * @return possibly newly created list
   */
  @SuppressWarnings("unchecked")
  public <L extends List> L addListProperty(final L list,
                                            final String name,
                                            final String val) {
    L theList = list;
    if (list == null) {
      theList = (L)new ArrayList();
    }

    theList.add(name + "=" + val);
    return theList;
  }

  /** Get a property stored as a String name = val
   *
   * @param col of property name+val
   * @param name of property
   * @return value or null
   */
  @ConfInfo(dontSave = true)
  public String getProperty(final Collection<String> col,
                            final String name) {
    final String key = name + "=";
    for (final String p: col) {
      if (p.startsWith(key)) {
        return p.substring(key.length());
      }
    }

    return null;
  }

  /** Remove a property stored as a String name = val
   *
   * @param col
   * @param name of property
   */
  public void removeProperty(final Collection<String> col,
                             final String name) {
    try {
      String v = getProperty(col, name);

      if (v == null) {
        return;
      }

      col.remove(name + "=" + v);
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /** Set a property
   *
   * @param list the list - possibly null
   * @param name of property
   * @param val of property
   * @return possibly newly created list
   */
  @SuppressWarnings("unchecked")
  public <L extends List> L setListProperty(final L list,
                                            final String name,
                                            final String val) {
    removeProperty(list, name);
    return addListProperty(list, name, val);
  }

  /**
   *
   * @param vals to be converted to a Properties object
   * @return the Properties
   */
  public static Properties toProperties(final List<String> vals) {
    try {
      final StringBuilder sb = new StringBuilder();

      for (final String p: vals) {
        sb.append(p);
        sb.append("\n");
      }

      final Properties pr = new Properties();

      pr.load(new StringReader(sb.toString()));

      return pr;
    } catch (final Throwable t) {
      throw new RuntimeException(t);
    }
  }

  /* ====================================================================
   *                   Save and restore methods
   * ==================================================================== */

  /** Output to a writer
   *
   * @param wtr
   * @throws ConfigException
   */
  public void toXml(final Writer wtr) throws ConfigException {
    try {
      final XmlEmit xml = new XmlEmit();
      xml.addNs(new NameSpace(ns, "BW"), true);
      xml.startEmit(wtr);

      dump(xml, false);
      xml.flush();
    } catch (final ConfigException cfe) {
      throw cfe;
    } catch (final Throwable t) {
      throw new ConfigException(t);
    }
  }

  /** XML root element must have type attribute
   * 
   * @param is an input stream
   * @return parsed notification or null
   * @throws ConfigException on error
   */
  public ConfigBase fromXml(final InputStream is) throws ConfigException {
    return fromXml(is, null);
  }

  /** XML root element must have type attribute if cl is null
   *
   * @param is an input stream
   * @param cl class of object or null
   * @return parsed notification or null
   * @throws ConfigException on error
   */
  public ConfigBase fromXml(final InputStream is,
                            final Class cl) throws ConfigException {
    try {
      return fromXml(parseXml(is), cl);
    } catch (final ConfigException ce) {
      throw ce;
    } catch (final Throwable t) {
      throw new ConfigException(t);
    }
  }

  /** XML root element must have type attribute
   *
   * @param rootEl - root of parsed document
   * @param cl class of object or null
   * @return parsed notification or null
   * @throws ConfigException on error
   */
  public ConfigBase fromXml(final Element rootEl,
                            final Class cl) throws ConfigException {
    try {
      final ConfigBase cb = (ConfigBase)getObject(rootEl, cl);

      if (cb == null) {
        // Can't do this
        return null;
      }

      for (final Element el: XmlUtil.getElementsArray(rootEl)) {
        populate(el, cb, null, null);
      }

      return cb;
    } catch (final ConfigException ce) {
      throw ce;
    } catch (final Throwable t) {
      throw new ConfigException(t);
    }
  }

  /* ====================================================================
   *                   Private from xml methods
   * ==================================================================== */

  private Object getObject(final Element el,
                           final Class cl) throws Throwable {
    Class objClass = cl;

    /* Element may have type attribute */
    final String type = XmlUtil.getAttrVal(el, "type");

    if ((type == null) && (objClass == null)) {
      error("Must supply a class or have type attribute");
      return null;
    }

    if (objClass == null) {
      //objClass = Class.forName(type);
      objClass = Thread.currentThread()
                       .getContextClassLoader()
                       .loadClass(type);
    }

    return objClass.getDeclaredConstructor().newInstance();
  }

  private static Element parseXml(final InputStream is) throws Throwable {
    final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);

    final DocumentBuilder builder = factory.newDocumentBuilder();

    final Document doc = builder.parse(new InputSource(is));

    if (doc == null) {
      return null;
    }

    return doc.getDocumentElement();
  }

  /** Populate either the object o via setters or the Collection col by adding
   * elements of type cl
   *
   * @param o
   * @param subroot
   * @param col
   * @param cl
   * @throws ConfigException
   */
  private void populate(final Element subroot,
                        final Object o,
                        final Collection<Object> col,
                        final Class cl) throws Throwable {
    final String name = subroot.getNodeName();

    Method meth = null;

    Class elClass = null;

    if (col == null) {
      /* We must have a setter */
      meth = findSetter(o, name);

      if (meth == null) {
        error("No setter for " + name);

        return;
      }

      /* We require a single parameter */

      final Class[] parClasses = meth.getParameterTypes();
      if (parClasses.length != 1) {
        error("Invalid setter method " + name);
        throw new ConfigException("Invalid setter method " + name);
      }

      elClass = parClasses[0];
    } else {
      elClass = cl;
    }

    if (!XmlUtil.hasChildren(subroot)) {
      /* A primitive value for which we should have a setter */

      final Object val = simpleValue(elClass, subroot, name);
      if (val != null) {
        assign(val, col, o, meth);
      }

      return;
    }

    /* There are children to this element. It either represents a complex type
     * or a collection.
     */

    if (Collection.class.isAssignableFrom(elClass)) {
      Collection<Object> colVal = null;

      if (elClass.getName().equals("java.util.Set")) {
        colVal = new TreeSet<Object>();
      } else if (elClass.getName().equals("java.util.List")) {
        colVal = new ArrayList<Object>();
      } else {
        error("Unsupported element class " + elClass +
              " for field " + name);
        return;
      }

      assign(colVal, col, o, meth);

      // Figure out the class of the elements
      /* I thought I might be able toextract it from the generic info -
       * Doesn't appear to be the case.
       *
      Type gft = elClass;

      if (!(gft instanceof ParameterizedType)) {
        error("Unsupported type " + elClass +
              " with name " + name);
        return;
      }

      Type[] fieldArgTypes = ((ParameterizedType)gft).getActualTypeArguments();

      if (fieldArgTypes.length != 1) {
        error("Unsupported type " + elClass +
              " with name " + name);
        return;
      }

      Class colElType = (Class)fieldArgTypes[0];
      */
      final ConfInfo ci = meth.getAnnotation(ConfInfo.class);

      final String colElTypeName;

      if (ci == null) {
        colElTypeName = "java.lang.String";
      } else {
        colElTypeName = ci.elementType();
      }

      for (final Element el: XmlUtil.getElementsArray(subroot)) {
        populate(el, o, colVal, Class.forName(colElTypeName));
      }

      return;
    }

    /* Asssume a complex type */

    final Object val = getObject(subroot, elClass);

    assign(val, col, o, meth);

    for (final Element el: XmlUtil.getElementsArray(subroot)) {
      populate(el, val, null, null);
    }
  }

  private Method findSetter(final Object val,
                            final String name) throws Throwable {
    final String methodName = "set" + name.substring(0, 1).toUpperCase() +
                        name.substring(1);
    final Method[] meths = val.getClass().getMethods();
    Method meth = null;

    for (final Method m: meths) {
      final ConfInfo ci = m.getAnnotation(ConfInfo.class);
      if ((ci != null) && ci.dontSave()) {
        continue;
      }

      if (m.getName().equals(methodName)) {
        if (meth != null) {
          throw new ConfigException(
                  "Multiple setters for field " + name);
        }
        meth = m;
      }
    }

    if (meth == null) {
      error("No setter method for property " + name +
                    " for class " + val.getClass().getName());
      return null;
    }

    return meth;
  }

  /** Assign a value - either to collection col or to a setter of o defined by meth
   * @param val
   * @param col - if non-null add to this.
   * @param o
   * @param meth
   * @throws Throwable
   */
  private void assign(final Object val,
                      final Collection<Object> col,
                      final Object o,
                      final Method meth) throws Throwable {
    if (col != null) {
      col.add(val);
    } else {
      final Object[] pars = new Object[]{val};

      meth.invoke(o, pars);
    }
  }

  private Object simpleValue(final Class cl,
                             final Element el,
                             final String name) throws Throwable {
    if (XmlUtil.hasChildren(el)) {
      // Complex value
      return null;
    }

    /* A primitive value for which we should have a setter */
    final String ndval = XmlUtil.getElementContent(el);
    if (ndval.length() == 0) {
      return null;
    }

    if (cl.getName().equals("java.lang.String")) {
      // Any tokens to replace?

      return Util.propertyReplace(ndval,
                                  new Util.PropertiesPropertyFetcher(System.getProperties()));
    }

    if (cl.getName().equals("int") ||
            cl.getName().equals("java.lang.Integer")) {
      return Integer.valueOf(ndval);
    }

    if (cl.getName().equals("long") ||
            cl.getName().equals("java.lang.Long")) {
      return Long.valueOf(ndval);
    }

    if (cl.getName().equals("boolean") ||
            cl.getName().equals("java.lang.Boolean")) {
      return Boolean.valueOf(ndval);
    }

    error("Unsupported par class " + cl +
                  " for field " + name);
    throw new ConfigException("Unsupported par class " + cl +
                                      " for field " + name);
  }

  /* ====================================================================
   *                   Private to xml methods
   * ==================================================================== */

  private static class ComparableMethod implements Comparable<ComparableMethod> {
    Method m;

    ComparableMethod(final Method m) {
      this.m = m;
    }

    @Override
    public int compareTo(final ComparableMethod that) {
      return this.m.getName().compareTo(that.m.getName());
    }
  }

  private void dump(final XmlEmit xml,
                    final boolean fromCollection) throws Throwable {
    final Class thisClass = getClass();
    final ConfInfo ciCl = (ConfInfo)thisClass.getAnnotation(ConfInfo.class);

    /* defClass defines the fields we need to dump */
    Class defClass = thisClass;
    String defClassName = null;

    if ((ciCl != null) && (ciCl.type().length() != 0)) {
      defClassName = ciCl.type();
    }

    if ((defClassName != null) &&
        !defClassName.equals(thisClass.getCanonicalName())) {
      final Class c = findClass(thisClass, defClassName);
      if (c != null) {
        defClass = c;
      }
    }

    final QName qn = startElement(xml, thisClass, ciCl);

    final Collection<ComparableMethod> ms = findGetters(defClass);

    for (final ComparableMethod cm: ms) {
      final Method m = cm.m;

      final ConfInfo ci = m.getAnnotation(ConfInfo.class);

      dumpValue(xml, m, ci, m.invoke(this, (Object[])null), fromCollection);
    }

    if (qn != null) {
      closeElement(xml, qn);
    }
  }

  private Class findClass(Class cl,
                          String cname) throws Throwable {
    /* Do interfaces first - it's usually one of those. */
    for (final Class c: cl.getInterfaces()) {
      if (c.getCanonicalName().equals(cname)) {
        return c;
      }

      final Class ic = findClass(c, cname);
      if (ic != null) {
        return ic;
      }
    }

    final Class c = cl.getSuperclass();
    if (c == null) {
      return null;
    }

    if (c.getCanonicalName().equals(cname)) {
      return c;
    }

    return findClass(c, cname);
  }

  /* Emit a start element with a name and type. The type is the name
     of the actual class.
   */
  private QName startElement(final XmlEmit xml,
                             final Class c,
                             final ConfInfo ci) throws Throwable {
    final QName qn;

    if (ci == null) {
      qn = new QName(ns, c.getName());
    } else {
      qn = new QName(ns, ci.elementName());
    }

    xml.openTag(qn, "type", c.getCanonicalName());

    return qn;
  }

  private QName startElement(final XmlEmit xml,
                             final Method m,
                             final ConfInfo ci,
                             final boolean fromCollection) throws Throwable {
    final QName qn = getTag(m, ci, fromCollection);

    if (qn != null) {
      xml.openTag(qn);
    }

    return qn;
  }

  private QName getTag(final Method m,
                       final ConfInfo ci,
                       final boolean fromCollection) {
    String tagName = null;

    if (ci != null) {
      if (!fromCollection) {
        if (ci.elementName().length() > 0) {
          tagName = ci.elementName();
        }
      } else if (ci.collectionElementName().length() > 0) {
        tagName = ci.collectionElementName();
      }
    }

    if ((tagName == null) && !fromCollection) {
      tagName = fieldName(m.getName());
    }

    if (tagName == null) {
      return null;
    }

    return new QName(tagName);
  }

  private String fieldName(final String val) {
    if (val.length() < 4) {
      return null;
    }

    return val.substring(3, 4).toLowerCase() + val.substring(4);
  }

  private void closeElement(final XmlEmit xml,
                            final QName qn) throws Throwable {
    xml.closeTag(qn);
  }

  private boolean dumpValue(final XmlEmit xml,
                            final Method m,
                            final ConfInfo ci,
                            final Object methVal,
                            final boolean fromCollection) throws Throwable {
    /* We always open the methodName or elementName tag if this is the method
     * value.
     *
     * If this is an element from a collection we generally don't want a tag.
     *
     * We do open a tag if the annotation specifies a collectionElementName
     */
    if (methVal instanceof ConfigBase) {
      final ConfigBase de = (ConfigBase)methVal;

      final QName mqn = startElement(xml, m, ci, fromCollection);

      de.dump(xml, false);

      if (mqn != null) {
        closeElement(xml, mqn);
      }

      return true;
    }

    if (methVal instanceof Collection) {
      final Collection c = (Collection)methVal;

      if (c.isEmpty()) {
        return false;
      }

      QName mqn = null;

      for (final Object o: c) {
        if (mqn == null) {
          mqn = startElement(xml, m, ci, fromCollection);
        }

        dumpValue(xml, m, ci, o, true);
      }

      if (mqn != null) {
        closeElement(xml, mqn);
      }

      return true;
    }

    property(xml, m, ci, methVal, fromCollection);

    return true;
  }

  private void property(final XmlEmit xml,
                        final Method m,
                        final ConfInfo d,
                        final Object p,
                        final boolean fromCollection) throws ConfigException {
    if (p == null) {
      return;
    }

    try {
      QName qn = getTag(m, d, fromCollection);

      if (qn == null) {
        /* Collection and no collection element name specified */
        qn = new QName(p.getClass().getName());
      }

      final String sval;

      if (p instanceof char[]) {
        sval = new String((char[])p);
      } else {
        sval = String.valueOf(p);
      }

      if ((sval.indexOf('&') < 0) && (sval.indexOf('<') < 0)) {
        xml.property(qn, sval);
      } else {
        xml.cdataProperty(qn, sval);
      }
    } catch (final Throwable t) {
      throw new ConfigException(t);
    }
  }

  private Collection<ComparableMethod> findGetters(Class cl) throws ConfigException {
    final Method[] meths = cl.getMethods();
    final Collection<ComparableMethod> getters = new TreeSet<ComparableMethod>();

    for (final Method m: meths) {
      final ConfInfo ci = m.getAnnotation(ConfInfo.class);

      if ((ci != null) && ci.dontSave()) {
        continue;
      }

      final String mname = m.getName();

      if (mname.length() < 4) {
        continue;
      }

      /* Name must start with get */
      if (!mname.startsWith("get")) {
        continue;
      }

      /* Don't want getClass */
      if (mname.equals("getClass")) {
        continue;
      }

      /* No parameters */
      final Class[] parClasses = m.getParameterTypes();
      if (parClasses.length != 0) {
        continue;
      }

      getters.add(new ComparableMethod(m));
    }

    return getters;
  }

  /* ====================================================================
   *                   Logged methods
   * ==================================================================== */

  private final BwLogger logger = new BwLogger();

  @Override
  public BwLogger getLogger() {
    if ((logger.getLoggedClass() == null) && (logger.getLoggedName() == null)) {
      logger.setLoggedClass(getClass());
    }

    return logger;
  }
}
