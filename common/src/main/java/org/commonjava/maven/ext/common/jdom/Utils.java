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
/*
 * Copyright (C) 2012 Apache Software Foundation
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
package org.commonjava.maven.ext.common.jdom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.TreeMap;

import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom2.Attribute;
import org.jdom2.CDATA;
import org.jdom2.Content;
import org.jdom2.DefaultJDOMFactory;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.experimental.UtilityClass;

@SuppressWarnings({ "JavaDoc", "RedundantModifiersUtilityClassLombok", "UnusedReturnValue" })
@UtilityClass
public final class Utils {

    private static final String INDENT = "  ";

    private static final String lineSeparator = "\n";

    private static final DefaultJDOMFactory factory = new DefaultJDOMFactory();

    private final Logger logger = LoggerFactory.getLogger(Utils.class);

    /**
     * Method updateElement.
     *
     * @param counter
     * @param shouldExist
     * @param name
     * @param parent
     * @return Element
     */
    public static Element updateElement(
            final IndentationCounter counter,
            final Element parent,
            final String name,
            final boolean shouldExist) {
        Element element = parent.getChild(name, parent.getNamespace());
        if (shouldExist) {
            if (element == null) {
                element = factory.element(name, parent.getNamespace());
                insertAtPreferredLocation(parent, element, counter);
            }
            counter.increaseCount();
        } else if (element != null) {
            final int index = parent.indexOf(element);
            if (index > 0) {
                final Content previous = parent.getContent(index - 1);
                if (previous instanceof Text) {
                    final Text txt = (Text) previous;
                    if (txt.getTextTrim().length() == 0) {
                        parent.removeContent(txt);
                    }
                }
            }
            parent.removeContent(element);
        }
        return element;
    } // -- Element updateElement( Counter, Element, String, boolean )

    /**
     * Method findAndReplaceXpp3DOM.
     *
     * @param counter
     * @param dom
     * @param name
     * @param parent
     * @return Element
     */
    public static Element findAndReplaceXpp3DOM(
            final IndentationCounter counter,
            final Element parent,
            final String name,
            final Xpp3Dom dom) {
        final boolean shouldExist = (dom != null) && (dom.getChildCount() > 0 || dom.getValue() != null);
        final Element element = updateElement(counter, parent, name, shouldExist);
        if (shouldExist) {
            replaceXpp3DOM(element, dom, new IndentationCounter(counter.getDepth() + 1));
        }
        return element;
    } // -- Element findAndReplaceXpp3DOM( Counter, Element, String, Xpp3Dom )

    /**
     * Method replaceXpp3DOM.
     *
     * @param parent
     * @param counter
     * @param parentDom
     */
    public static void replaceXpp3DOM(final Element parent, final Xpp3Dom parentDom, final IndentationCounter counter) {
        if (parentDom.getChildCount() > 0) {
            final Xpp3Dom[] childs = parentDom.getChildren();
            final ArrayList<Xpp3Dom> domChilds = new ArrayList<>(Arrays.asList(childs));
            final ListIterator<?> it = parent.getChildren().listIterator();
            while (it.hasNext()) {
                final Element elem = (Element) it.next();
                final Iterator<Xpp3Dom> it2 = domChilds.iterator();
                Xpp3Dom corrDom = null;
                while (it2.hasNext()) {
                    final Xpp3Dom dm = it2.next();
                    // Elem::getName, unlike getQualifiedName, does not return namespaced names which can fail the comparison
                    if (dm.getName().equals(elem.getQualifiedName())) {
                        corrDom = dm;
                        break;
                    }
                }
                if (corrDom != null) {
                    domChilds.remove(corrDom);
                    replaceXpp3DOM(elem, corrDom, new IndentationCounter(counter.getDepth() + 1));
                    counter.increaseCount();
                } else {
                    it.remove();
                }
            }
            for (Xpp3Dom dm : domChilds) {
                final String rawName = dm.getName();
                final String[] parts = rawName.split(":");

                Element elem;
                if (parts.length > 1) {
                    final String nsId = parts[0];
                    String nsUrl = dm.getAttribute("xmlns:" + nsId);
                    final String name = parts[1];
                    if (nsUrl == null) {
                        nsUrl = parentDom.getAttribute("xmlns:" + nsId);
                    }
                    elem = factory.element(name, Namespace.getNamespace(nsId, nsUrl));
                } else {
                    Namespace root = parent.getNamespace();
                    for (Namespace n : getNamespacesInherited(parent)) {
                        if (n.getPrefix() == null || n.getPrefix().length() == 0) {
                            root = n;
                            break;
                        }
                    }
                    elem = factory.element(dm.getName(), root);
                }

                final String[] attributeNames = dm.getAttributeNames();
                for (final String attrName : attributeNames) {
                    if (attrName.startsWith("xmlns:")) {
                        continue;
                    }
                    elem.setAttribute(attrName, dm.getAttribute(attrName));
                }

                insertAtPreferredLocation(parent, elem, counter);
                counter.increaseCount();
                replaceXpp3DOM(elem, dm, new IndentationCounter(counter.getDepth() + 1));
            }
        } else if (parentDom.getValue() != null) {
            List<Content> cl = parent.getContent();
            boolean foundCdata = false;
            for (Content c : cl) {
                if (c instanceof CDATA) {
                    foundCdata = true;
                    break;
                }
            }

            if (!foundCdata) {
                if (parent.getText().trim().equals(parentDom.getValue())) {
                    logger.trace(
                            "Ignoring during element update as original of '{}' equals (ignoring trimmed whitespace) '{}'",
                            parent.getText(),
                            parentDom.getValue());
                } else {
                    parent.setText(parentDom.getValue());
                }
            }
        }
    } // -- void replaceXpp3DOM( Element, Xpp3Dom, Counter )

    /**
     * Method insertAtPreferredLocation.
     *
     * @param parent
     * @param counter
     * @param child
     */
    public static void insertAtPreferredLocation(
            final Element parent,
            final Element child,
            final IndentationCounter counter) {
        int contentIndex = 0;
        int elementCounter = 0;
        final Iterator<?> it = parent.getContent().iterator();
        Text lastText = null;
        int offset = 0;
        while (it.hasNext() && elementCounter <= counter.getCurrentIndex()) {
            final Object next = it.next();
            offset = offset + 1;
            if (next instanceof Element) {
                elementCounter = elementCounter + 1;
                contentIndex = contentIndex + offset;
                offset = 0;
            }
            if (next instanceof Text && it.hasNext()) {
                lastText = (Text) next;
            }
        }
        if (lastText != null && lastText.getTextTrim().length() == 0) {
            lastText = lastText.clone();
        } else {
            StringBuilder starter = new StringBuilder(lineSeparator);
            for (int i = 0; i < counter.getDepth(); i++) {
                starter.append(INDENT);
            }
            lastText = factory.text(starter.toString());
        }
        if (parent.getContentSize() == 0) {
            final Text finalText = lastText.clone();
            final String newVersion = finalText.getText().substring(0, finalText.getText().length() - INDENT.length());
            // TODO: Not sure if we need to handle this text replacement specially (like elsewhere).
            logger.trace("Replacing original text of {} with modified text of {}", finalText.getText(), newVersion);
            finalText.setText(newVersion);
            parent.addContent(contentIndex, finalText);
        }
        parent.addContent(contentIndex, child);
        parent.addContent(contentIndex, lastText);
    } // -- void insertAtPreferredLocation( Element, Element, Counter )

    /**
     * Method findAndReplaceProperties.
     *
     * @param counter
     * @param props
     * @param name
     * @param parent
     * @return Element
     */
    public static Element findAndReplaceProperties(
            final IndentationCounter counter,
            final Element parent,
            final String name,
            final Properties props) {
        final boolean shouldExist = (props != null) && !props.isEmpty();
        final Element element = updateElement(counter, parent, name, shouldExist);
        if (shouldExist) {
            Iterator<?> it = props.stringPropertyNames().iterator();
            final IndentationCounter innerCounter = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final String key = (String) it.next();
                findAndReplaceSimpleElement(innerCounter, element, key, (String) props.get(key), null);
            }
            final ArrayList<String> lst = new ArrayList<>(props.stringPropertyNames());
            it = element.getChildren().iterator();
            while (it.hasNext()) {
                final Element elem = (Element) it.next();
                final String key = elem.getName();
                if (!lst.contains(key)) {
                    it.remove();
                }
            }
        }
        return element;
    } // -- Element findAndReplaceProperties( Counter, Element, String, Map )

    /**
     * Method findAndReplaceSimpleElement.
     *
     * @param counter
     * @param defaultValue
     * @param text
     * @param name
     * @param parent
     * @return Element
     */
    public static Element findAndReplaceSimpleElement(
            final IndentationCounter counter,
            final Element parent,
            final String name,
            final String text,
            final String defaultValue) {
        if ((defaultValue != null) && defaultValue.equals(text)) {
            final Element element = parent.getChild(name, parent.getNamespace());
            // if exist and is default value or if doesn't exist.. just keep the way it is..
            if (element == null || defaultValue.equals(element.getText())) {
                return element;
            }
        }
        // TODO: IntelliJ warns this is always true but having .length() > 0 removes elements by mistake.
        @SuppressWarnings("ConstantConditions")
        final boolean shouldExist = (text != null) && (text.trim().length() >= 0);
        final Element element = updateElement(counter, parent, name, shouldExist);
        if (shouldExist) {
            if (element.getText().trim().equals(text)) {
                logger.trace(
                        "Ignoring during element update as original of '{}' equals (ignoring trimmed whitespace) '{}'",
                        element.getText(),
                        text);
            } else {
                element.setText(text);
            }
        }
        return element;
    } // -- Element findAndReplaceSimpleElement( Counter, Element, String, String, String )

    /**
     * Method findAndReplaceSimpleLists.
     *
     * @param counter
     * @param childName
     * @param parentName
     * @param list
     * @param parent
     * @return Element
     */
    public static Element findAndReplaceSimpleLists(
            final IndentationCounter counter,
            final Element parent,
            final Collection<?> list,
            final String parentName,
            final String childName) {
        final boolean shouldExist = (list != null) && (list.size() > 0);
        final Element element = updateElement(counter, parent, parentName, shouldExist);
        if (shouldExist) {
            final Iterator<?> it = list.iterator();
            Iterator<?> elIt = element.getChildren(childName, element.getNamespace()).iterator();
            if (!elIt.hasNext()) {
                elIt = null;
            }
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final String value = (String) it.next();
                Element el;
                if (elIt != null && elIt.hasNext()) {
                    el = (Element) elIt.next();
                    if (!elIt.hasNext()) {
                        elIt = null;
                    }
                } else {
                    el = factory.element(childName, element.getNamespace());
                    insertAtPreferredLocation(element, el, innerCount);
                }
                // TODO: Not sure if we need to handle this text replacement specially (like elsewhere).
                logger.trace("TODO: Replacing original text of {} with modified text of {}", el.getText(), value);
                el.setText(value);
                innerCount.increaseCount();
            }
            if (elIt != null) {
                while (elIt.hasNext()) {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
        return element;
    } // -- Element findAndReplaceSimpleLists( Counter, Element, java.util.Collection, String, String )

    // Copied from JDOM2
    static List<Namespace> getNamespacesInherited(Element element) {
        if (element.getParentElement() == null) {
            ArrayList<Namespace> ret = new ArrayList<>(getNamespacesInScope(element));
            for (Iterator<Namespace> it = ret.iterator(); it.hasNext();) {
                Namespace ns = it.next();
                if (ns == Namespace.NO_NAMESPACE || ns == Namespace.XML_NAMESPACE) {
                    continue;
                }
                it.remove();
            }
            return Collections.unmodifiableList(ret);
        }

        // OK, the things we inherit are the prefixes we have in scope that
        // are also in our parent's scope.
        HashMap<String, Namespace> parents = new HashMap<>();
        for (Namespace ns : getNamespacesInScope(element.getParentElement())) {
            parents.put(ns.getPrefix(), ns);
        }

        ArrayList<Namespace> al = new ArrayList<>();
        for (Namespace ns : getNamespacesInScope(element)) {
            if (ns == parents.get(ns.getPrefix())) {
                // inherited
                al.add(ns);
            }
        }

        return Collections.unmodifiableList(al);
    }

    /**
     * Get the Namespaces that are in-scope on this Element. Element has the
     * most complex rules for the namespaces-in-scope.
     * <p>
     * The scope is built up from a number of sources following the rules of
     * XML namespace inheritence as follows:
     * <ul>
     * <li>The {@link Namespace#XML_NAMESPACE} is added
     * <li>The element's namespace is added (commonly
     * {@link Namespace#NO_NAMESPACE})
     * <li>All the attributes are inspected and their Namespaces are included
     * <li>If the element has a parent then the parent's Namespace scope is
     * inspected, and any prefixes in the parent scope that are not yet bound
     * in this Element's scope are included.
     * <li>If the default Namespace (the no-prefix namespace) has not been
     * encountered for this Element then {@link Namespace#NO_NAMESPACE} is
     * included.
     * </ul>
     * The Element's Namespace scope consist of it's inherited Namespaces and
     * any modifications to that scope derived from the Element itself. If the
     * element is detached then it's inherited scope consists of just
     * If an element has no parent then
     * <p>
     * Note that the Element's Namespace will always be reported first.
     * {@inheritDoc}
     *
     */
    // Copied from JDOM2
    static List<Namespace> getNamespacesInScope(Element element) {
        // The assumption here is that all namespaces are valid,
        // that there are no namespace collisions on this element

        // This method is also the 'anchor' of the three getNamespaces*() methods
        // It does not make reference to this Element instance's other
        // getNamespace*() methods

        TreeMap<String, Namespace> namespaces = new TreeMap<>();
        namespaces.put(Namespace.XML_NAMESPACE.getPrefix(), Namespace.XML_NAMESPACE);
        namespaces.put(element.getNamespacePrefix(), element.getNamespace());
        if (element.getAdditionalNamespaces() != null) {
            for (Namespace ns : element.getAdditionalNamespaces()) {
                if (!namespaces.containsKey(ns.getPrefix())) {
                    namespaces.put(ns.getPrefix(), ns);
                }
            }
        }
        if (element.getAttributes() != null) {
            for (Attribute att : element.getAttributes()) {
                Namespace ns = att.getNamespace();
                if (!namespaces.containsKey(ns.getPrefix())) {
                    namespaces.put(ns.getPrefix(), ns);
                }
            }
        }
        // Right, we now have all the namespaces that are current on this Element.
        // Include any other namespaces that are inherited.
        final Element pnt = element.getParentElement();
        if (pnt != null) {
            for (Namespace ns : getNamespacesInScope(pnt)) {
                if (!namespaces.containsKey(ns.getPrefix())) {
                    namespaces.put(ns.getPrefix(), ns);
                }
            }
        }

        if (pnt == null && !namespaces.containsKey("")) {
            // we are the root element, and there is no 'default' namespace.
            namespaces.put(Namespace.NO_NAMESPACE.getPrefix(), Namespace.NO_NAMESPACE);
        }

        ArrayList<Namespace> al = new ArrayList<>(namespaces.size());
        al.add(element.getNamespace());
        namespaces.remove(element.getNamespacePrefix());
        al.addAll(namespaces.values());

        return Collections.unmodifiableList(al);
    }
}
