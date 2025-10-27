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
package org.commonjava.maven.ext.core.impl;

import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomUtils;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.ArtifactPluginWrapper;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.util.WildcardMap;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.CommonState;
import org.commonjava.maven.ext.core.state.PluginState;
import org.commonjava.maven.ext.core.state.PluginState.PluginPrecedence;
import org.commonjava.maven.ext.core.state.PluginState.Precedence;
import org.commonjava.maven.ext.core.state.RESTState;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.commonjava.maven.ext.core.util.PropertyMapper;
import org.commonjava.maven.ext.io.ModelIO;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.commonjava.maven.ext.core.util.IdUtils.ga;

/**
 * {@link Manipulator} implementation that can alter plugin sections in a project's pom file.
 * Configuration is stored in a {@link PluginState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("plugin-manipulator")
@Singleton
public class PluginManipulator extends CommonManipulator implements Manipulator
{
    private enum PluginType
    {
        // Attempting to configure using remote plugins is not supported - just remote plugin management (like dependencyManagement).
        RemotePM,
        LocalPM,
        LocalP;

        @Override
        public String toString()
        {
            switch ( this )
            {
                case RemotePM:
                    return "RemotePluginManagement";
                case LocalPM:
                    return "LocalPluginManagement";
                case LocalP:
                    return "LocalPlugins";
                default:
                    throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Used to store mappings of old property to new version.
     */
    private final Map<Project,Map<String, PropertyMapper>> versionPropertyUpdateMap = new LinkedHashMap<>();

    @Inject
    public PluginManipulator(ModelIO effectiveModelBuilder)
    {
        this.effectiveModelBuilder = effectiveModelBuilder;
    }

    /**
     * Initialize the {@link PluginState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init( final ManipulationSession session ) throws ManipulationException
    {
        this.session = session;
        session.setState( new PluginState( session.getUserProperties() ) );
    }

    /**
     * Apply the alignment changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
        throws ManipulationException
    {
        final PluginState state = session.getState( PluginState.class );
        final CommonState cState = session.getState( CommonState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( getClass().getSimpleName() + ": Nothing to do!" );
            return Collections.emptySet();
        }

        final Set<Project> changed = new HashSet<>();
        final Set<Plugin> mgmtOverrides = loadRemoteBOM();

        for ( final Project project : projects )
        {
            final Model model = project.getModel();

            if (!mgmtOverrides.isEmpty() || !state.getPluginOverride().isEmpty())
            {
                apply( project, model, mgmtOverrides );

                changed.add( project );
            }
        }
        // If we've changed something now update any old properties with the new values.
        if ( !changed.isEmpty() )
        {
            if ( cState.getStrictDependencyPluginPropertyValidation() > 0 )
            {
                logger.info( "Iterating to validate plugin updates..." );
                for ( final Project p : versionPropertyUpdateMap.keySet() )
                {
                    validatePluginsUpdatedProperty( cState, p, p.getResolvedPlugins( session ) );
                    for ( final Map<ProjectVersionRef, Plugin> dependencies :
                          p.getResolvedProfilePlugins( session ).values() )
                    {
                        validatePluginsUpdatedProperty( cState, p, dependencies );
                    }
                }
            }
            logger.info( "Iterating for property overrides...{}", versionPropertyUpdateMap );
            for ( final Entry<Project, Map<String, PropertyMapper>> e : versionPropertyUpdateMap.entrySet() )
            {
                final Project project = e.getKey();
                for ( final Entry<String, PropertyMapper> entry : e.getValue().entrySet() )
                {
                    final String key = entry.getKey();
                    final String newVersion = entry.getValue().getNewVersion();
                    // Ignore strict alignment for plugins ; if we're attempting to use a differing plugin
                    // its unlikely to be an exact match.
                    final PropertiesUtils.PropertyUpdate found =
                            PropertiesUtils.updateProperties( session, project , true, key, newVersion );

                    if ( found == PropertiesUtils.PropertyUpdate.NOTFOUND )
                    {
                        // Problem in this scenario is that we know we have a property update map but we have not found a
                        // property to update. Its possible this property has been inherited from a parent. Override in the
                        // top pom for safety.
                        logger.info( "Unable to find a property for {} to update", entry.getKey() );
                        for ( final Project p : changed )
                        {
                            if ( p.isInheritanceRoot() )
                            {
                                logger.info( "Adding property {} with {}", key, newVersion );
                                p.getModel().getProperties().setProperty( key, newVersion );
                            }
                        }
                    }
                }
            }
        }
        return changed;
    }


    private Set<Plugin> loadRemoteBOM()
        throws ManipulationException
    {
        final RESTState rState = session.getState( RESTState.class );
        final PluginState pState = session.getState( PluginState.class );
        final Set<Plugin> restOverrides = pState.getRemoteRESTOverrides();
        final Set<Plugin> bomOverrides = new LinkedHashSet<>();
        final List<ProjectVersionRef> gavs = pState.getRemotePluginMgmt();

        Set<Plugin> mergedOverrides = new LinkedHashSet<>();

        if ( gavs != null )
        {
            // We used to iterate in reverse order so that the first GAV in the list overwrites the last
            // but due to the simplification moving to a single Set, as that doesn't support replace operation,
            // we now iterate in normal order.
            final Iterator<ProjectVersionRef> iter = gavs.iterator();
            final Properties exclusions = (Properties) session.getUserProperties().clone();
            exclusions.putAll( System.getProperties() );

            while ( iter.hasNext() )
            {
                final ProjectVersionRef ref = iter.next();
                bomOverrides.addAll( effectiveModelBuilder.getRemotePluginManagementVersionOverrides( ref, exclusions ) );
            }
        }

        if ( pState.getPrecedence() == PluginPrecedence.BOM )
        {
            mergedOverrides = bomOverrides;
            if ( mergedOverrides.isEmpty() )
            {
                String msg = rState.isEnabled() ? "pluginSource for restURL" : "pluginManagement";

                logger.warn( "No dependencies found for pluginSource {}. Has {} been configured? ", pState.getPrecedence(), msg );
            }
        }
        if ( pState.getPrecedence() == PluginPrecedence.REST )
        {
            mergedOverrides = restOverrides;
            if ( mergedOverrides.isEmpty() )
            {
                logger.warn( "No dependencies found for pluginSource {}. Has restURL been configured? ", pState.getPrecedence() );
            }
        }
        else if ( pState.getPrecedence() == PluginPrecedence.RESTBOM )
        {
            mergedOverrides = restOverrides;
            mergedOverrides.addAll( bomOverrides );
        }
        else if ( pState.getPrecedence() == PluginPrecedence.BOMREST )
        {
            mergedOverrides = bomOverrides;
            mergedOverrides.addAll( restOverrides );
        }

        logger.debug( "Final remote override list for type {} with precedence {} is {}", PluginType.RemotePM, pState.getPrecedence(), mergedOverrides );

        return mergedOverrides;
    }

    private void apply( final Project project, final Model model, final Set<Plugin> overrides )
        throws ManipulationException
    {
        if ( logger.isDebugEnabled() )
        {
            logger.debug( "Applying plugin changes for {} to: {}", PluginType.RemotePM, ga( project ) );
        }

        final PluginState pluginState = session.getState( PluginState.class );

        // Map of Group : Map of artifactId [ may be wildcard ] : value
        final WildcardMap<String> explicitOverrides = new WildcardMap<>();
        final String projectGA = ga( project );
        final Map<ArtifactRef, String> originalOverrides = new HashMap<>();
        overrides.forEach( p -> originalOverrides.put( new ArtifactPluginWrapper( p ), p.getVersion()) );

        final Map<ArtifactRef, String> originalOverridesReduced = applyModuleVersionOverrides( projectGA, pluginState.getPluginOverride(), originalOverrides, explicitOverrides, Collections.emptyMap() );

        // Now we have a reduced list of wrapper plugins (due to removing those are being explicitly overridden).
        // Therefore reflect that in the original plugin list.
        final Iterator<Plugin> it = overrides.iterator();
        while (it.hasNext())
        {
            final Plugin existing = it.next();
            if ( originalOverridesReduced.keySet().stream().anyMatch( a -> ((ArtifactPluginWrapper)a).getOriginal().equals( existing ) ) )
            {
                continue;
            }
            // Couldn't find it so remove it.
            it.remove();
        }

        if ( project.isInheritanceRoot() )
        {
            // If the model doesn't have any plugin management set by default, create one for it
            Build build = model.getBuild();

            if ( build == null )
            {
                build = new Build();
                model.setBuild( build );
                logger.debug( "Created new Build for model {}", model.getId() );
            }

            PluginManagement pluginManagement = model.getBuild().getPluginManagement();

            if ( pluginManagement == null )
            {
                pluginManagement = new PluginManagement();
                model.getBuild().setPluginManagement( pluginManagement );
                logger.debug( "Created new Plugin Management for model {}", model.getId() );
            }

            // Override plugin management versions
            applyOverrides( project, PluginType.LocalPM, project.getResolvedManagedPlugins( session ), overrides );
        }

        applyOverrides( project, PluginType.LocalP, project.getResolvedPlugins( session ), overrides );
        applyExplicitOverrides( project, project.getResolvedPlugins( session ), explicitOverrides,
                                explicitVersionPropertyUpdateMap );
        applyExplicitOverrides( project, project.getResolvedManagedPlugins( session ), explicitOverrides,
                                explicitVersionPropertyUpdateMap );

        final Map<Profile, Map<ProjectVersionRef, Plugin>> pd = project.getResolvedProfilePlugins( session );
        final Map<Profile, Map<ProjectVersionRef, Plugin>> pmd = project.getResolvedProfileManagedPlugins( session );

        logger.debug( "Processing profiles with plugin management" );
        for ( final Map<ProjectVersionRef, Plugin> plugins : pmd.values() )
        {
            applyOverrides( project, PluginType.LocalPM, plugins, overrides );
            applyExplicitOverrides( project, plugins, explicitOverrides, explicitVersionPropertyUpdateMap );
        }
        logger.debug( "Processing profiles with plugins" );
        for ( final Map<ProjectVersionRef, Plugin> plugins : pd.values() )
        {
            applyOverrides( project, PluginType.LocalP, plugins, overrides );
            applyExplicitOverrides( project, plugins, explicitOverrides, explicitVersionPropertyUpdateMap );
        }

        explicitOverridePropertyUpdates( session );
    }

    /**
     * Set the versions of any plugins which match the contents of the list of plugin overrides.
     *
     * Currently this method takes the remote plugin type (note that remote plugins are deprecated) and the local plugin type.
     * It will ONLY apply configurations, executions and dependencies from the remote pluginMgmt to the local pluginMgmt.
     *   If the local pluginMgmt does not have a matching plugin then, if {@link CommonState#isOverrideTransitive()} is true
     * then it will inject a new plugin into the local pluginMgmt.
     *   It will however apply version changes to both local pluginMgmt and local plugins.
     * Note that if the deprecated injectRemotePlugins is enabled then remote plugin version, executions, dependencies and
     * configurations will also be applied to the local plugins.
     *
     * @param project the current project
     * @param localPluginType The type of local block (mgmt or plugins). Only used to determine whether to inject configs/deps/executions.
     * @param plugins The list of plugins to modify
     * @param pluginVersionOverrides The list of version overrides to apply to the plugins
     * @throws ManipulationException if an error occurs.
     */
    // TODO:  Pass in explicitOverrides to avoid potential property clashes.
    private void applyOverrides( Project project, final PluginType localPluginType, final Map<ProjectVersionRef, Plugin> plugins,
                                 final Set<Plugin> pluginVersionOverrides ) throws ManipulationException
    {
        if ( plugins == null )
        {
            throw new ManipulationException( "Original plugins should not be null" );
        }

        final PluginState pluginState = session.getState( PluginState.class );
        final CommonState commonState = session.getState( CommonState.class );
        final HashMap<String, ProjectVersionRef> pluginsByGA = new LinkedHashMap<>(  );
        // Secondary map of original plugins group:artifact to pvr mapping.
        for ( ProjectVersionRef pvr : plugins.keySet() )
        {
            // We should NEVER have multiple group:artifact with different versions in the same project. If we do,
            // like with dependencies, the behaviour is undefined - although its most likely the last-wins.
            pluginsByGA.put( pvr.asProjectRef().toString(), pvr );
        }

        for ( final Plugin override : pluginVersionOverrides )
        {
            Plugin plugin = null;
            String newValue = override.getVersion();

            // If we're doing strict matching then we need to see if there is a matching plugin with the
            // same version. Problem is when we have been run previously i.e. plugins contains rebuild-x
            // and we want to compare without suffix. How do we establish the original version versus the
            // override version.
            if ( pluginsByGA.containsKey( override.getKey() ) )
            {
                // Potential match of override group:artifact to original plugin group:artifact.
                String oldValue = pluginsByGA.get( override.getKey() ).getVersionString();
                plugin = plugins.get( pluginsByGA.get( override.getKey() ) );

                if ( plugin.getVersion().equals( Version.PROJECT_VERSION ) || ( plugin.getVersion().contains( "$" ) && project.getVersion().equals( oldValue ) ))
                {
                    logger.warn( "Plugin {} for {} references ${project.version} so skipping.", plugin.getId(), project.getPom() );
                }
                else if ( commonState.isStrict() )
                {
                    if ( !PropertiesUtils.checkStrictValue( session, oldValue, newValue ) )
                    {
                        if ( commonState.isFailOnStrictViolation() )
                        {
                            throw new ManipulationException(
                                            "Plugin reference {} replacement: {} of original version: {} violates the strict version-alignment rule!",
                                            plugin.getId(), newValue, oldValue );
                        }
                        else
                        {
                            logger.warn( "Plugin reference {} replacement: {} of original version: {} violates the strict version-alignment rule!",
                                         plugin.getId(), newValue, oldValue );
                            // Ignore the dependency override. As found has been set to true it won't inject
                            // a new property either.
                            continue;
                        }
                    }
                }
            }

            logger.debug( "Plugin override {} and local plugin {} with remotePluginType {} / localPluginType {}", override.getId(), plugin,
                          PluginType.RemotePM, localPluginType );

            if ( plugin != null )
            {
                if ( localPluginType == PluginType.LocalPM )
                {
                    if ( override.getConfiguration() != null )
                    {
                        logger.debug( "Injecting plugin configuration {}", override.getConfiguration() );
                        if ( plugin.getConfiguration() == null )
                        {
                            plugin.setConfiguration( override.getConfiguration() );
                            logger.debug( "Altered plugin configuration: {}={}", plugin.getKey(), plugin.getConfiguration() );
                        }
                        else if ( plugin.getConfiguration() != null )
                        {
                            logger.debug( "Existing plugin configuration: {}", plugin.getConfiguration() );

                            if ( !( plugin.getConfiguration() instanceof Xpp3Dom ) || !( override.getConfiguration() instanceof Xpp3Dom ) )
                            {
                                throw new ManipulationException("Incorrect DOM type {} and {}",
                                                                plugin.getConfiguration().getClass().getName(),
                                                                override.getConfiguration().getClass().getName() );
                            }

                            if ( pluginState.getConfigPrecedence() == Precedence.REMOTE )
                            {
                                plugin.setConfiguration(
                                                Xpp3DomUtils.mergeXpp3Dom( (Xpp3Dom) override.getConfiguration(), (Xpp3Dom) plugin.getConfiguration() ) );
                            }
                            else if ( pluginState.getConfigPrecedence() == Precedence.LOCAL )
                            {
                                plugin.setConfiguration( Xpp3DomUtils.mergeXpp3Dom( (Xpp3Dom) plugin.getConfiguration(),
                                                                                    (Xpp3Dom) override.getConfiguration() ) );
                            }
                            logger.debug( "Altered plugin configuration: {}={}", plugin.getKey(), plugin.getConfiguration() );
                        }
                    }
                    else
                    {
                        logger.debug( "No remote configuration to inject from {}", override );
                    }

                    if ( override.getExecutions() != null )
                    {
                        Map<String, PluginExecution> newExecutions = override.getExecutionsAsMap();
                        Map<String, PluginExecution> originalExecutions = plugin.getExecutionsAsMap();

                        for ( PluginExecution pe : newExecutions.values() )
                        {
                            if ( originalExecutions.containsKey( pe.getId() ) )
                            {
                                logger.warn( "Unable to inject execution {} as it clashes with an existing execution",
                                        pe.getId());
                            }
                            else
                            {
                                logger.debug( "Injecting execution {}", pe );
                                plugin.getExecutions().add( pe );
                            }
                        }
                    }
                    else
                    {
                        logger.debug( "No remote executions to inject from {}", override );
                    }

                    if ( !override.getDependencies().isEmpty() )
                    {
                        // TODO: ### Review this - is it still required?
                        logger.debug( "Checking original plugin dependencies versus override" );
                        // First, remove any Dependency from the original Plugin if the GA exists in the override.
                        Iterator<Dependency> originalIt = plugin.getDependencies().iterator();
                        while ( originalIt.hasNext() )
                        {
                            Dependency originalD = originalIt.next();
                            for ( Dependency newD : override.getDependencies() )
                            {
                                if ( originalD.getGroupId().equals( newD.getGroupId() ) && originalD.getArtifactId()
                                                                                                    .equals( newD.getArtifactId() ) )
                                {
                                    logger.debug( "Removing original dependency {} in favour of {}", originalD, newD );
                                    originalIt.remove();
                                    break;
                                }
                            }
                        }
                        // Now merge them together. Only inject dependencies in the management block.
                        logger.debug( "Adding in plugin dependencies {}", override.getDependencies() );
                        plugin.getDependencies().addAll( override.getDependencies() );
                    }
                }

                // Explicitly using the original non-resolved original version.
                String oldVersion = plugin.getVersion();
                // Always force the version in a pluginMgmt block or set the version if there is an existing
                // one in build/plugins section.

                // Due to StandardMaven304PluginDefaults::getDefault version returning "[0.0.0.1]" override version
                // will never be null.
                if ( !PropertiesUtils.cacheProperty( session, project, versionPropertyUpdateMap, oldVersion,
                                                     newValue, plugin, false ) )
                {
                    if ( oldVersion != null && oldVersion.equals( Version.PROJECT_VERSION ) )
                    {
                        logger.debug( "For plugin {} ; version is built in {} so skipping inlining {}", plugin,
                                      oldVersion, newValue );
                    }
                    else if ( oldVersion != null && oldVersion.contains( "${" ) )
                    {
                        throw new ManipulationException( "NYI : Multiple embedded properties for plugins." );
                    }
                    else
                    {
                        plugin.setVersion( newValue );
                        logger.info( "Altered plugin version: {}={}", override.getKey(), newValue );
                    }
                }
            }
            // If the plugin doesn't exist but has a configuration section in the remote inject it so we
            // get the correct config.
            else if ( localPluginType == PluginType.LocalPM && commonState.isOverrideTransitive() && ( override.getConfiguration() != null
                            || override.getExecutions().size() > 0 ) )
            {
                project.getModel().getBuild().getPluginManagement().getPlugins().add( override );
                logger.info( "Added plugin version: {}={}", override.getKey(), newValue );
            }
        }
    }

    private void validatePluginsUpdatedProperty( CommonState cState, Project p, Map<ProjectVersionRef, Plugin> dependencies )
                    throws ManipulationException
    {
        for ( final Entry<ProjectVersionRef, Plugin> entry : dependencies.entrySet() )
        {
            final ProjectVersionRef d = entry.getKey();
            final Plugin plugin = entry.getValue();
            final String pluginVersion = plugin.getVersion();

            if ( startsWith( pluginVersion, "${" ) )
            {
                PropertiesUtils.verifyPropertyMapping( cState, p, versionPropertyUpdateMap, d,
                                                       PropertiesUtils.extractPropertyName( pluginVersion ) );
            }
        }
    }

    @Override
    public int getExecutionIndex()
    {
        return 35;
    }
}
