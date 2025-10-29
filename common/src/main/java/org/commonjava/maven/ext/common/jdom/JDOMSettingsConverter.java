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

package org.commonjava.maven.ext.common.jdom;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.commonjava.maven.ext.common.jdom.Utils.findAndReplaceProperties;
import static org.commonjava.maven.ext.common.jdom.Utils.findAndReplaceSimpleElement;
import static org.commonjava.maven.ext.common.jdom.Utils.findAndReplaceSimpleLists;
import static org.commonjava.maven.ext.common.jdom.Utils.findAndReplaceXpp3DOM;
import static org.commonjava.maven.ext.common.jdom.Utils.insertAtPreferredLocation;
import static org.commonjava.maven.ext.common.jdom.Utils.updateElement;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.settings.Activation;
import org.apache.maven.settings.ActivationFile;
import org.apache.maven.settings.ActivationOS;
import org.apache.maven.settings.ActivationProperty;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.RepositoryPolicy;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.JDOMFactory;
import org.jdom2.UncheckedJDOMFactory;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * Class SettingsJDOMWriter.
 *
 * @version $Revision$ $Date$
 */
@SuppressWarnings({ "rawtypes", "JavaDoc" })
public class JDOMSettingsConverter {
    /**
     * Field factory.
     */
    private final JDOMFactory factory;

    private final Format format = Format.getRawFormat();

    // ----------------/
    // - Constructors -/
    // ----------------/

    public JDOMSettingsConverter() {
        factory = new UncheckedJDOMFactory();
    } // -- org.apache.maven.settings.io.jdom.SettingsJDOMWriter()

    // -----------/
    // - Methods -/
    // -----------/
    public final void write(final Settings source, final File target, final String intro, String outtro)
            throws IOException, JDOMException {
        final SAXBuilder builder = new SAXBuilder();
        final Document document;

        // CVE-2021-33813  https://github.com/hunterhacker/jdom/issues/189
        builder.setExpandEntities(false);

        // TODO: Improve this.
        // If we are building from an existing file then use that as a base otherwise we need to construct the Document
        // and root Element.
        if (target.length() > 0) {
            document = builder.build(target);
        } else {
            document = builder.build(
                    new StringReader(
                            "<settings xmlns=\"http://maven.apache.org/SETTINGS/1.0.0\" "
                                    + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                                    + "xsi:schemaLocation=\"http://maven.apache.org/SETTINGS/1.0.0 "
                                    + "http://maven.apache.org/xsd/settings-1.0.0.xsd\"></settings>"));
        }

        format.setTextMode(Format.TextMode.PRESERVE);
        format.setOmitEncoding(false);
        format.setOmitDeclaration(false);
        format.setExpandEmptyElements(false);
        format.setEncoding(
                StringUtils.isEmpty(source.getModelEncoding()) ? StandardCharsets.UTF_8.toString()
                        : source.getModelEncoding());

        update(source, new IndentationCounter(0), document.getRootElement());

        final XMLOutputter outputter = new XMLOutputter();
        outputter.setFormat(format);

        try (Writer settingsWriter = WriterFactory.newWriter(target, format.getEncoding())) {
            if (isNotBlank(intro)) {
                settingsWriter.write(intro);
                outputter.output(document.getRootElement(), settingsWriter);
                settingsWriter.write(outtro);
            } else {
                outputter.output(document, settingsWriter);
            }
            settingsWriter.flush();
        }
    }

    public void setLineSeparator(final String separator) {
        format.setLineSeparator(separator);
    }

    /**
     * Method iterateMirror.
     * 
     * @param counter
     * @param parent
     * @param list
     */
    protected void iterateMirror(final IndentationCounter counter, final Element parent, final Collection list) {
        final boolean shouldExist = (list != null) && (list.size() > 0);
        final Element element = updateElement(counter, parent, "mirrors", shouldExist);
        if (shouldExist) {
            final Iterator it = list.iterator();
            Iterator elIt = element.getChildren("mirror", element.getNamespace()).iterator();
            if (!elIt.hasNext()) {
                elIt = null;
            }
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final Mirror value = (Mirror) it.next();
                Element el;
                if (elIt != null && elIt.hasNext()) {
                    el = (Element) elIt.next();
                    if (!elIt.hasNext()) {
                        elIt = null;
                    }
                } else {
                    el = factory.element("mirror", element.getNamespace());
                    insertAtPreferredLocation(element, el, innerCount);
                }
                updateMirror(value, innerCount, el);
                innerCount.increaseCount();
            }
            if (elIt != null) {
                while (elIt.hasNext()) {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateMirror( Counter, Element, java.util.Collection, java.lang.String, java.lang.String )

    /**
     * Method iterateProfile.
     * 
     * @param counter
     * @param parent
     * @param list
     */
    protected void iterateProfile(final IndentationCounter counter, final Element parent, final Collection list) {
        final boolean shouldExist = (list != null) && (list.size() > 0);
        final Element element = updateElement(counter, parent, "profiles", shouldExist);
        if (shouldExist) {
            final Iterator it = list.iterator();
            Iterator elIt = element.getChildren("profile", element.getNamespace()).iterator();
            if (!elIt.hasNext()) {
                elIt = null;
            }
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final Profile value = (Profile) it.next();
                Element el;
                if (elIt != null && elIt.hasNext()) {
                    el = (Element) elIt.next();
                    if (!elIt.hasNext()) {
                        elIt = null;
                    }
                } else {
                    el = factory.element("profile", element.getNamespace());
                    insertAtPreferredLocation(element, el, innerCount);
                }
                updateProfile(value, innerCount, el);
                innerCount.increaseCount();
            }
            if (elIt != null) {
                while (elIt.hasNext()) {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateProfile( Counter, Element, java.util.Collection, java.lang.String, java.lang.String )

    /**
     * Method iterateProxy.
     * 
     * @param counter
     * @param parent
     * @param list
     */
    protected void iterateProxy(final IndentationCounter counter, final Element parent, final Collection list) {
        final boolean shouldExist = (list != null) && (list.size() > 0);
        final Element element = updateElement(counter, parent, "proxies", shouldExist);
        if (shouldExist) {
            final Iterator it = list.iterator();
            Iterator elIt = element.getChildren("proxy", element.getNamespace()).iterator();
            if (!elIt.hasNext()) {
                elIt = null;
            }
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final Proxy value = (Proxy) it.next();
                Element el;
                if (elIt != null && elIt.hasNext()) {
                    el = (Element) elIt.next();
                    if (!elIt.hasNext()) {
                        elIt = null;
                    }
                } else {
                    el = factory.element("proxy", element.getNamespace());
                    insertAtPreferredLocation(element, el, innerCount);
                }
                updateProxy(value, innerCount, el);
                innerCount.increaseCount();
            }
            if (elIt != null) {
                while (elIt.hasNext()) {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateProxy( Counter, Element, java.util.Collection, java.lang.String, java.lang.String )

    /**
     * Method iterateRepository.
     *
     * @param counter
     * @param childTag
     * @param parentTag
     * @param list
     * @param parent
     */
    protected void iterateRepository(
            final IndentationCounter counter,
            final Element parent,
            final java.util.Collection list,
            final java.lang.String parentTag,
            final java.lang.String childTag) {
        final boolean shouldExist = (list != null) && (list.size() > 0);
        final Element element = updateElement(counter, parent, parentTag, shouldExist);
        if (shouldExist) {
            final Iterator it = list.iterator();
            Iterator elIt = element.getChildren(childTag, element.getNamespace()).iterator();
            if (!elIt.hasNext()) {
                elIt = null;
            }
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final Repository value = (Repository) it.next();
                Element el;
                if (elIt != null && elIt.hasNext()) {
                    el = (Element) elIt.next();
                    if (!elIt.hasNext()) {
                        elIt = null;
                    }
                } else {
                    el = factory.element(childTag, element.getNamespace());
                    insertAtPreferredLocation(element, el, innerCount);
                }
                updateRepository(value, innerCount, el);
                innerCount.increaseCount();
            }
            if (elIt != null) {
                while (elIt.hasNext()) {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateRepository( Counter, Element, java.util.Collection, java.lang.String, java.lang.String )

    /**
     * Method iterateServer.
     * 
     * @param counter
     * @param parent
     * @param list
     */
    protected void iterateServer(final IndentationCounter counter, final Element parent, final Collection list) {
        final boolean shouldExist = (list != null) && (list.size() > 0);
        final Element element = updateElement(counter, parent, "servers", shouldExist);
        if (shouldExist) {
            final Iterator it = list.iterator();
            Iterator elIt = element.getChildren("server", element.getNamespace()).iterator();
            if (!elIt.hasNext()) {
                elIt = null;
            }
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            while (it.hasNext()) {
                final Server value = (Server) it.next();
                Element el;
                if (elIt != null && elIt.hasNext()) {
                    el = (Element) elIt.next();
                    if (!elIt.hasNext()) {
                        elIt = null;
                    }
                } else {
                    el = factory.element("server", element.getNamespace());
                    insertAtPreferredLocation(element, el, innerCount);
                }
                updateServer(value, innerCount, el);
                innerCount.increaseCount();
            }
            if (elIt != null) {
                while (elIt.hasNext()) {
                    elIt.next();
                    elIt.remove();
                }
            }
        }
    } // -- void iterateServer( Counter, Element, java.util.Collection, java.lang.String, java.lang.String )

    /**
     * Method updateActivation.
     * 
     * @param activation
     * @param counter
     * @param element
     */
    protected void updateActivation(
            final Activation activation,
            final IndentationCounter counter,
            final Element element) {
        final boolean shouldExist = (activation != null);
        final Element root = updateElement(counter, element, "activation", shouldExist);
        if (shouldExist) {
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "activeByDefault",
                    String.valueOf(activation.isActiveByDefault()),
                    "false");
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "jdk",
                    activation.getJdk(),
                    null);
            updateActivationOS(activation.getOs(), innerCount, root);
            updateActivationProperty(activation.getProperty(), innerCount, root);
            updateActivationFile(activation.getFile(), innerCount, root);
        }
    } // -- void updateActivation( Activation, String, Counter, Element )

    /**
     * Method updateActivationFile.
     * 
     * @param activationFile
     * @param counter
     * @param element
     */
    protected void updateActivationFile(
            final ActivationFile activationFile,
            final IndentationCounter counter,
            final Element element) {
        final boolean shouldExist = (activationFile != null);
        final Element root = updateElement(counter, element, "file", shouldExist);
        if (shouldExist) {
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            findAndReplaceSimpleElement(innerCount, root, "missing", activationFile.getMissing(), null);
            findAndReplaceSimpleElement(innerCount, root, "exists", activationFile.getExists(), null);
        }
    } // -- void updateActivationFile( ActivationFile, String, Counter, Element )

    /**
     * Method updateActivationOS.
     * 
     * @param activationOS
     * @param counter
     * @param element
     */
    protected void updateActivationOS(
            final ActivationOS activationOS,
            final IndentationCounter counter,
            final Element element) {
        final boolean shouldExist = (activationOS != null);
        final Element root = updateElement(counter, element, "os", shouldExist);
        if (shouldExist) {
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "name",
                    activationOS.getName(),
                    null);
            findAndReplaceSimpleElement(innerCount, root, "family", activationOS.getFamily(), null);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "arch",
                    activationOS.getArch(),
                    null);
            findAndReplaceSimpleElement(innerCount, root, "version", activationOS.getVersion(), null);
        }
    } // -- void updateActivationOS( ActivationOS, String, Counter, Element )

    /**
     * Method updateActivationProperty.
     * 
     * @param activationProperty
     * @param counter
     * @param element
     */
    protected void updateActivationProperty(
            final ActivationProperty activationProperty,
            final IndentationCounter counter,
            final Element element) {
        final boolean shouldExist = (activationProperty != null);
        final Element root = updateElement(counter, element, "property", shouldExist);
        if (shouldExist) {
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "name",
                    activationProperty.getName(),
                    null);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "value",
                    activationProperty.getValue(),
                    null);
        }
    } // -- void updateActivationProperty( ActivationProperty, String, Counter, Element )

    /**
     * Method updateMirror.
     * 
     * @param mirror
     * @param counter
     * @param element
     */
    protected void updateMirror(final Mirror mirror, final IndentationCounter counter, final Element element) {
        final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "mirrorOf",
                mirror.getMirrorOf(),
                null);
        findAndReplaceSimpleElement(innerCount, element, "name", mirror.getName(), null);
        findAndReplaceSimpleElement(innerCount, element, "url", mirror.getUrl(), null);
        if (element.getNamespace().getURI().equals("http://maven.apache.org/SETTINGS/1.1.0")) {
            findAndReplaceSimpleElement(innerCount, element, "layout", mirror.getLayout(), null);
            findAndReplaceSimpleElement(
                    innerCount,
                    element,
                    "mirrorOfLayouts",
                    mirror.getMirrorOfLayouts(),
                    "default,legacy");
        }
        findAndReplaceSimpleElement(innerCount, element, "id", mirror.getId(), "default");
    } // -- void updateMirror( Mirror, String, Counter, Element )

    /**
     * Method updateProfile.
     *
     * @param profile
     * @param element
     * @param counter
     */
    protected void updateProfile(final Profile profile, final IndentationCounter counter, final Element element) {
        final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
        updateActivation(profile.getActivation(), innerCount, element);
        findAndReplaceProperties(innerCount, element, "properties", profile.getProperties());
        iterateRepository(innerCount, element, profile.getRepositories(), "repositories", "repository");
        iterateRepository(
                innerCount,
                element,
                profile.getPluginRepositories(),
                "pluginRepositories",
                "pluginRepository");
        findAndReplaceSimpleElement(innerCount, element, "id", profile.getId(), "default");
    } // -- void updateProfile( Profile, String, Counter, Element )

    /**
     * Method updateProxy.
     *
     * @param proxy
     * @param element
     * @param counter
     */
    protected void updateProxy(final Proxy proxy, final IndentationCounter counter, final Element element) {
        final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "active",
                String.valueOf(proxy.isActive()),
                "true");
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "protocol",
                proxy.getProtocol(),
                "http");
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "username",
                proxy.getUsername(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "password",
                proxy.getPassword(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "port",
                String.valueOf(proxy.getPort()),
                "8080");
        findAndReplaceSimpleElement(innerCount, element, "host", proxy.getHost(), null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "nonProxyHosts",
                proxy.getNonProxyHosts(),
                null);
        findAndReplaceSimpleElement(innerCount, element, "id", proxy.getId(), "default");
    } // -- void updateProxy( Proxy, String, Counter, Element )

    /**
     * Method updateRepository.
     *
     * @param repository
     * @param element
     * @param counter
     */
    protected void updateRepository(
            final Repository repository,
            final IndentationCounter counter,
            final Element element) {
        final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
        updateRepositoryPolicy(repository.getReleases(), "releases", innerCount, element);
        updateRepositoryPolicy(repository.getSnapshots(), "snapshots", innerCount, element);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "id",
                repository.getId(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "name",
                repository.getName(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "url",
                repository.getUrl(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "layout",
                repository.getLayout(),
                "default");
    } // -- void updateRepository( Repository, String, Counter, Element )

    /**
     * Method updateRepositoryPolicy.
     *
     * @param repositoryPolicy
     * @param element
     * @param counter
     * @param xmlTag
     */
    protected void updateRepositoryPolicy(
            final RepositoryPolicy repositoryPolicy,
            final String xmlTag,
            final IndentationCounter counter,
            final Element element) {
        final boolean shouldExist = (repositoryPolicy != null);
        final Element root = updateElement(counter, element, xmlTag, shouldExist);
        if (shouldExist) {
            final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "enabled",
                    String.valueOf(repositoryPolicy.isEnabled()),
                    "true");
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "updatePolicy",
                    repositoryPolicy.getUpdatePolicy(),
                    null);
            findAndReplaceSimpleElement(
                    innerCount,
                    root,
                    "checksumPolicy",
                    repositoryPolicy.getChecksumPolicy(),
                    null);
        }
    } // -- void updateRepositoryPolicy( RepositoryPolicy, String, Counter, Element )

    /**
     * Method updateServer.
     * 
     * @param server
     * @param counter
     * @param element
     */
    protected void updateServer(final Server server, final IndentationCounter counter, final Element element) {
        final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "username",
                server.getUsername(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "password",
                server.getPassword(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "privateKey",
                server.getPrivateKey(),
                null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "passphrase",
                server.getPassphrase(),
                null);
        findAndReplaceSimpleElement(innerCount, element, "filePermissions", server.getFilePermissions(), null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "directoryPermissions",
                server.getDirectoryPermissions(),
                null);
        findAndReplaceXpp3DOM(innerCount, element, "configuration", (Xpp3Dom) server.getConfiguration());
        findAndReplaceSimpleElement(innerCount, element, "id", server.getId(), "default");
    } // -- void updateServer( Server, String, Counter, Element )

    /**
     * Method updateSettings.
     * 
     * @param settings
     * @param counter
     * @param element
     */
    protected void updateSettings(final Settings settings, final IndentationCounter counter, final Element element) {
        final IndentationCounter innerCount = new IndentationCounter(counter.getDepth() + 1);
        findAndReplaceSimpleElement(innerCount, element, "localRepository", settings.getLocalRepository(), null);
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "interactiveMode",
                String.valueOf(settings.isInteractiveMode()),
                "true");
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "usePluginRegistry",
                String.valueOf(settings.isUsePluginRegistry()),
                "false");
        findAndReplaceSimpleElement(
                innerCount,
                element,
                "offline",
                !settings.isOffline() ? null : String.valueOf(settings.isOffline()),
                "false");
        iterateProxy(innerCount, element, settings.getProxies());
        iterateServer(innerCount, element, settings.getServers());
        iterateMirror(innerCount, element, settings.getMirrors());
        iterateProfile(innerCount, element, settings.getProfiles());
        findAndReplaceSimpleLists(innerCount, element, settings.getActiveProfiles(), "activeProfiles", "activeProfile");
        findAndReplaceSimpleLists(innerCount, element, settings.getPluginGroups(), "pluginGroups", "pluginGroup");
    } // -- void updateSettings( Settings, String, Counter, Element )

    protected void update(
            final Settings source,
            final IndentationCounter indentationCounter,
            final Element rootElement) {
        updateSettings(source, indentationCounter, rootElement);
    }
}
