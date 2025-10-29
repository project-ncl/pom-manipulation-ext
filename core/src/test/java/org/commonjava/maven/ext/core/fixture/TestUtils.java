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
package org.commonjava.maven.ext.core.fixture;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.ManipulationUncheckedException;
import org.commonjava.maven.ext.core.ManipulationManager;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.slf4j.LoggerFactory;

import lombok.Getter;

/**
 * Common test utilities.
 */
public final class TestUtils {
    /**
     * The test root directory.
     */
    public static final Path ROOT_DIRECTORY = resolveFileResource("", "").getParentFile()
            .getParentFile()
            .getParentFile()
            .toPath();

    /**
     * The integration test directory.
     */
    public static final Path INTEGRATION_TEST = ROOT_DIRECTORY.resolve("integration-test");

    /**
     * The URL for maven central.
     */
    public static final String MVN_CENTRAL = "https://repo1.maven.org/maven2";

    /**
     * Resolves a resource as a maven model.
     *
     * @param resourceBase the resource base directory
     * @param resourceName the resource name
     * @return the maven model
     * @throws Exception if an error occurs while reading the resource
     */
    public static Model resolveModelResource(final String resourceBase, final String resourceName)
            throws Exception {
        try (Reader reader = Files.newBufferedReader(resolveFileResource(resourceBase, resourceName).toPath())) {
            return new MavenXpp3Reader().read(reader);
        }
    }

    /**
     * Resolves a resource as a file.
     *
     * @param resourceBase the resource base directory
     * @param resourceName the resource name
     * @return the file
     */
    public static File resolveFileResource(final String resourceBase, final String resourceName) {
        final String separator = StringUtils.isNotEmpty(resourceBase) ? "/" : "";
        final URL resource = Thread.currentThread()
                .getContextClassLoader()
                .getResource(resourceBase + separator + resourceName);

        if (resource == null) {
            throw new ManipulationUncheckedException(
                    "Unable to locate resource for {}{}",
                    resourceBase,
                    resourceName);
        }
        return new File(resource.getPath());
    }

    /**
     * Gets a dummy model.
     *
     * @return the dummy model
     */
    public static Model getDummyModel() {
        Model result = new Model();
        result.setGroupId("org.commonjava.maven.ext");
        result.setArtifactId("dummy-model");
        result.setVersion("1.0.0-SNAPSHOT");
        return result;
    }

    // Public API for manipulator-examples-project
    /**
     * Creates a new manipulation session from the given properties and the given POM file.
     *
     * @param p the properties
     * @param pom the POM file
     * @return the manipulation session
     * @throws ManipulationException if an error occurs while creating the session
     */
    public static ManipulationSession createSession(Properties p, File pom) throws ManipulationException {
        return createSessionAndManager(p, pom).getSession();
    }

    // Public API for manipulator-examples-project
    /**
     * Creates a new manipulation session from the given properties.
     *
     * @param p the properties
     * @return the manipulation session
     * @throws ManipulationException if an error occurs while creating the session
     */
    public static ManipulationSession createSession(Properties p) throws ManipulationException {
        return createSessionAndManager(p, null).getSession();
    }

    // Public API for manipulator-examples-project
    /**
     * Creates a new manipulation session/manager from the given properties.
     *
     * @param p the properties
     * @return the manipulation session/manager
     * @throws ManipulationException if an error occurs while creating the session/manager
     */
    @SuppressWarnings("deprecation")
    public static SMContainer createSessionAndManager(Properties p) throws ManipulationException {
        return createSessionAndManager(p, null);
    }

    // Public API for manipulator-examples-project
    /**
     * Creates a new manipulation session/manager from the given properties and the given POM file.
     *
     * @param p the properties
     * @param pom the POM file
     * @return the manipulation session/manager
     * @throws ManipulationException if an error occurs while creating the session/manager
     */
    @SuppressWarnings("deprecation")
    public static SMContainer createSessionAndManager(Properties p, File pom) throws ManipulationException {
        ManipulationSession session;

        final ArtifactRepository ar = new MavenArtifactRepository(
                "test",
                MVN_CENTRAL,
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(),
                new ArtifactRepositoryPolicy());

        final MavenExecutionRequest req = new DefaultMavenExecutionRequest()
                .setSystemProperties(System.getProperties())
                .setUserProperties(p)
                .setRemoteRepositories(Collections.singletonList(ar));
        final PlexusContainer container;
        final ManipulationManager manipulationManager;
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();

        config.setClassPathScanning(PlexusConstants.SCANNING_ON);
        config.setComponentVisibility(PlexusConstants.GLOBAL_VISIBILITY);
        config.setName("PME");

        LoggerFactory.getLogger(TestUtils.class)
                .info("Creating session with Maven Central using configuration PME");

        try {
            container = new DefaultPlexusContainer(config);
            manipulationManager = container.lookup(ManipulationManager.class);
            session = container.lookup(ManipulationSession.class);
        } catch (PlexusContainerException | ComponentLookupException e) {
            throw new ManipulationException("Unable to create DefaultPlexusContainer", e);
        }
        final MavenSession mavenSession = new MavenSession(container, null, req, new DefaultMavenExecutionResult());

        mavenSession.getRequest().setPom(pom);

        session.setMavenSession(mavenSession);
        manipulationManager.init(session);

        return new SMContainer(session, manipulationManager);
    }

    /**
     * Executes a method on an object instance. The name and parameters of
     * the method are specified. The method will be executed and the value
     * of it returned, even if the method would have private or protected access.
     *
     * @param instance the object the underlying method is invoked from
     * @param name the name of the method
     * @param types the parameter array
     * @param params the arguments used for the method call
     * @throws Exception if an error occurs while executing the method
     */
    public static Object executeMethod(Object instance, String name, Class<?>[] types, Object[] params)
            throws Exception {
        Class<?> c = instance.getClass();

        Method m;
        try {
            m = c.getDeclaredMethod(name, types);
        } catch (NoSuchMethodException e) {
            c = c.getSuperclass();
            m = c.getDeclaredMethod(name, types);
        }

        m.setAccessible(true);

        return m.invoke(instance, params);
    }

    /**
     * Container object to allow a {@link ManipulationSession} to be created but also return the
     * {@link ManipulationManager}.
     */
    public static class SMContainer {
        /**
         * Gets the session.
         *
         * @return the session
         */
        @Getter
        private final ManipulationSession session;

        /**
         * Gets the manager.
         *
         * @return the manager
         */
        @Getter
        private final ManipulationManager manager;

        private SMContainer(ManipulationSession session, ManipulationManager manager) {
            this.session = session;
            this.manager = manager;
        }
    }
}
