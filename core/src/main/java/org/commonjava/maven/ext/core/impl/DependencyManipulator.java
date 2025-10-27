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

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.InvalidRefException;
import org.commonjava.atlas.maven.ident.ref.ProjectRef;
import org.commonjava.atlas.maven.ident.ref.ProjectVersionRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectRef;
import org.commonjava.atlas.maven.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.core.util.PluginReference;
import org.commonjava.maven.ext.core.util.DependencyPluginWrapper;
import org.commonjava.maven.ext.common.model.Project;
import org.commonjava.maven.ext.common.model.SimpleScopedArtifactRef;
import org.commonjava.maven.ext.common.util.PropertyResolver;
import org.commonjava.maven.ext.common.util.WildcardMap;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.state.CommonState;
import org.commonjava.maven.ext.core.state.DependencyState;
import org.commonjava.maven.ext.core.state.DependencyState.DependencyPrecedence;
import org.commonjava.maven.ext.core.state.RESTState;
import org.commonjava.maven.ext.core.util.DependencyPluginUtils;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.commonjava.maven.ext.core.util.PropertyMapper;
import org.commonjava.maven.ext.io.ModelIO;
import org.commonjava.maven.ext.io.resolver.GalleyAPIWrapper;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.commonjava.maven.ext.core.util.IdUtils.ga;

/**
 * {@link Manipulator} implementation that can alter dependency (and dependency management) sections in a project's pom file.
 * Configuration is stored in a {@link DependencyState} instance, which is in turn stored in the {@link ManipulationSession}.
 */
@Named("project-dependency-manipulator")
@Singleton
public class DependencyManipulator extends CommonManipulator implements Manipulator
{
    private final GalleyAPIWrapper galleyWrapper;

    /**
     * Used to store mappings of old property to new version - the new version is encapsulated within the {@link PropertyMapper}
     * which also contains reference to the old version and the dependency that changed this. This allows complete tracking of
     * dependencies that updated properties - and therefore, the inverse, dependencies that did NOT update the property. This can
     * be problematic in the case of rebuilds.
     */
    private final Map<Project,Map<String, PropertyMapper>> versionPropertyUpdateMap = new LinkedHashMap<>();

    @Inject
    public DependencyManipulator(ModelIO effectiveModelBuilder, GalleyAPIWrapper galleyWrapper)
    {
        this.effectiveModelBuilder = effectiveModelBuilder;
        this.galleyWrapper = galleyWrapper;
    }

    /**
     * Initialize the {@link DependencyState} state holder in the {@link ManipulationSession}. This state holder detects
     * version-change configuration from the Maven user properties (-D properties from the CLI) and makes it available for
     * later.
     */
    @Override
    public void init( final ManipulationSession session ) throws ManipulationException
    {
        session.setState( new DependencyState( session.getUserProperties() ) );
        this.session = session;
    }

    /**
     * Apply the alignment changes to the list of {@link Project}'s given.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects )
                    throws ManipulationException
    {
        final DependencyState state = session.getState( DependencyState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( "{}: Nothing to do!", getClass().getSimpleName() );
            return Collections.emptySet();
        }
        return internalApplyChanges( projects, loadRemoteOverrides() );
    }

    /**
     * This will load the remote overrides. It will first try to load any overrides that might have
     * been prepopulated by the REST scanner, failing that it will load from a remote POM file.
     *
     * @return the loaded overrides
     * @throws ManipulationException if an error occurs.
     */
    private Map<ArtifactRef, String> loadRemoteOverrides() throws ManipulationException
    {
        final DependencyState depState = session.getState( DependencyState.class );
        final RESTState restState = session.getState( RESTState.class );
        final List<ProjectVersionRef> gavs = depState.getRemoteBOMDepMgmt();

        final Map<String, ProjectVersionRef> extraGAVs = depState.getExtraBOMs();
        final Map<String, Map<ProjectRef, String>> extraBOMOverrides = depState.getExtraBOMDepMgmts();

        // While in theory we are only mapping ProjectRef -> NewVersion if we store key as ProjectRef we can't then have
        // org.foo:foobar -> 1.2.0.redhat-2
        // org.foo:foobar -> 2.0.0.redhat-2
        // Which is useful for strictAlignment scenarios (although undefined for non-strict).
        final Map<ArtifactRef, String> restOverrides = depState.getRemoteRESTOverrides();
        final Map<ArtifactRef, String> bomOverrides = new LinkedHashMap<>();
        Map<ArtifactRef, String> mergedOverrides = new LinkedHashMap<>();

        logger.info( "Remote precedence is {}", depState.getPrecedence() );
        if ( gavs != null )
        {
            final ListIterator<ProjectVersionRef> iter = gavs.listIterator( gavs.size() );
            // Iterate in reverse order so that the first GAV in the list overwrites the last
            while ( iter.hasPrevious() )
            {
                final ProjectVersionRef ref = iter.previous();
                Map<ArtifactRef, String> rBom = effectiveModelBuilder.getRemoteDependencyVersionOverrides( ref );

                // We don't normalise the BOM list here as ::applyOverrides can handle multiple GA with different V
                // for strict override. However, it is undefined if strict is not enabled.
                bomOverrides.putAll( rBom );
            }
        }

        // Load extra BOMs into separate maps for accessing later, when applying the dependencyExclusions.
        for ( Entry<String, ProjectVersionRef> entry : extraGAVs.entrySet() )
        {
            extraBOMOverrides.put( entry.getKey(),
                                   effectiveModelBuilder.getRemoteDependencyVersionOverridesByProject( entry.getValue() ) );
        }

        if ( depState.getPrecedence() == DependencyPrecedence.BOM )
        {
            mergedOverrides = bomOverrides;
            if ( mergedOverrides.isEmpty() )
            {
                String msg = restState.isEnabled() ? "dependencySource for restURL" : "dependencyManagement";

                logger.warn( "No dependencies found for dependencySource {}. Has {} been configured? ", depState.getPrecedence(), msg );
            }
        }
        else if ( depState.getPrecedence() == DependencyPrecedence.REST )
        {
            mergedOverrides = restOverrides;
            if ( mergedOverrides.isEmpty() )
            {
                logger.warn( "No dependencies found for dependencySource {}. Has restURL been configured? ", depState.getPrecedence() );
            }
        }
        else if ( depState.getPrecedence() == DependencyPrecedence.RESTBOM )
        {
            mergedOverrides = bomOverrides;

            removeDuplicateArtifacts( mergedOverrides, restOverrides );
            mergedOverrides.putAll( restOverrides );
        }
        else if ( depState.getPrecedence() == DependencyPrecedence.BOMREST )
        {
            mergedOverrides = restOverrides;
            removeDuplicateArtifacts( mergedOverrides, bomOverrides );
            mergedOverrides.putAll( bomOverrides );
        }

        logger.debug( "Final remote override list is {}", mergedOverrides );
        return mergedOverrides;
    }


    private void removeDuplicateArtifacts( Map<ArtifactRef, String> mergedOverrides, Map<ArtifactRef, String> targetOverrides )
    {
        final Iterator<Entry<ArtifactRef, String>> it = mergedOverrides.entrySet().iterator();
        while ( it.hasNext() )
        {
            final Entry<ArtifactRef, String> mergedOverridesEntry = it.next();
            final ArtifactRef key = mergedOverridesEntry.getKey();
            final ProjectRef pRef = key.asProjectRef();

            for ( final Entry<ArtifactRef, String> tatgetOverridesEntry : targetOverrides.entrySet() )
            {
                final ArtifactRef target = tatgetOverridesEntry.getKey();

                if ( pRef.equals( target.asProjectRef() ) )
                {
                    logger.debug( "Merging sources ; entry {}={} clashes (and will be removed) with precedence given to {}={}",
                                  key, mergedOverridesEntry.getValue(), target, tatgetOverridesEntry.getValue() );
                    it.remove();
                    break;
                }
            }
        }
    }

    @Override
    public int getExecutionIndex()
    {
        return 40;
    }

    private Set<Project> internalApplyChanges( final List<Project> projects, Map<ArtifactRef, String> overrides )
                    throws ManipulationException
    {
        final DependencyState state = session.getState( DependencyState.class );
        final CommonState cState = session.getState( CommonState.class );
        final Set<Project> result = new HashSet<>( projects.size() );

        for ( final Project project : projects )
        {
            final Model model = project.getModel();

            if ( !overrides.isEmpty() || !state.getDependencyOverrides().isEmpty() )
            {
                apply( project, model, overrides );

                result.add( project );
            }
        }

        // If we've changed something now update any old properties with the new values.
        if ( !result.isEmpty() )
        {
            if ( cState.getStrictDependencyPluginPropertyValidation() > 0 )
            {
                logger.info( "Iterating to validate dependency updates..." );
                for ( final Project p : versionPropertyUpdateMap.keySet() )
                {
                    validateDependenciesUpdatedProperty( cState, p, p.getResolvedDependencies( session ) );
                    for ( final Map<ArtifactRef, Dependency> dependencies :
                          p.getResolvedProfileDependencies( session ).values() )
                    {
                        validateDependenciesUpdatedProperty( cState, p, dependencies );
                    }
                }
            }

            logger.info( "Iterating for property overrides...{}", versionPropertyUpdateMap );
            for ( final Entry<Project, Map<String, PropertyMapper>> e : versionPropertyUpdateMap.entrySet() )
            {
                final Project project = e.getKey();
                final Map<String, PropertyMapper> map = e.getValue();
                logger.debug( "Checking property override within project {}", project );

                for ( final Entry<String, PropertyMapper> entry : map.entrySet() )
                {
                    final String key = entry.getKey();
                    final PropertyMapper mapper = entry.getValue();
                    final String newVersion = mapper.getNewVersion();
                    final PropertiesUtils.PropertyUpdate found = PropertiesUtils.updateProperties( session, project,
                            false, key, newVersion );

                    if ( found == PropertiesUtils.PropertyUpdate.NOTFOUND )
                    {
                        // Problem in this scenario is that we know we have a property update map but we have not found a
                        // property to update. Its possible this property has been inherited from a parent. Override in the
                        // top pom for safety.
                        logger.info( "Unable to find a property for {} to update", key );
                        logger.info( "Adding property {} with {}", key, newVersion );
                        // We know the inheritance root is at position 0 in the inherited list...
                        project.getInheritedList().get( 0 ).getModel().getProperties().setProperty( key, newVersion );
                    }
                }
            }

            explicitOverridePropertyUpdates( session );
        }

        return result;
    }

    /**
     * Applies dependency overrides to the project.
     */
    private void apply( final Project project, final Model model, final Map<ArtifactRef, String> overrides )
                    throws ManipulationException
    {
        // Map of Group : Map of artifactId [ may be wildcard ] : value
        final WildcardMap<String> explicitOverrides = new WildcardMap<>();
        final String projectGA = ga( project );
        final DependencyState dependencyState = session.getState( DependencyState.class );
        final CommonState commonState = session.getState( CommonState.class );

        logger.debug( "Processing project {}", projectGA );

        Map<ArtifactRef, String> originalOverrides = new LinkedHashMap<>( overrides );
        originalOverrides = removeReactorGAs( originalOverrides );

        logger.debug( "Using dependencyOverride of {}", dependencyState.getDependencyOverrides() );
        try
        {
            originalOverrides = applyModuleVersionOverrides( projectGA, dependencyState.getDependencyOverrides(),
                                                           originalOverrides, explicitOverrides,
                                                           dependencyState.getExtraBOMDepMgmts() );
            logger.debug( "Module overrides are:{}{}", System.lineSeparator(), originalOverrides );
            logger.debug( "Explicit overrides are:{}{}", System.lineSeparator(), explicitOverrides );
        }
        catch ( InvalidRefException e )
        {
            logger.error( "Invalid module exclusion override {} : {}", originalOverrides, explicitOverrides );
            throw e;
        }

        if ( project.isInheritanceRoot() )
        {
            // Handle the situation where the top level parent refers to a prior build that is in the BOM.
            if ( project.getModelParent() != null )
            {
                for ( Entry<ArtifactRef, String> entry : originalOverrides.entrySet() )
                {
                    final ArtifactRef pvr = entry.getKey();
                    final String oldValue = project.getModelParent().getVersion();
                    final String newValue = entry.getValue();

                    if ( pvr.asProjectRef().equals( SimpleProjectRef.parse( ga( project.getModelParent() ) ) ) )
                    {
                        if ( commonState.isStrict() )
                        {
                            if ( !PropertiesUtils.checkStrictValue( session, oldValue, newValue ) )
                            {
                                if ( commonState.isFailOnStrictViolation() )
                                {
                                    throw new ManipulationException(
                                                    "Parent reference {} replacement: {} of original version: {} violates the strict version-alignment rule!",
                                                    ga( project.getModelParent() ), newValue, oldValue );
                                }
                                else
                                {
                                    logger.warn( "Parent reference {}:{} replacement: {} of original version: {} violates the strict version-alignment rule!",
                                            project.getModelParent().getGroupId(),
                                            project.getModelParent().getArtifactId(), newValue, oldValue );
                                    // Ignore the dependency override. As found has been set to true it won't inject
                                    // a new property either.
                                    continue;
                                }
                            }
                        }

                        logger.debug( "Modifying parent reference from {} to {} for {}:{}",
                                model.getParent().getVersion(), newValue, project.getModelParent().getGroupId(),
                                project.getModelParent().getArtifactId() );
                        model.getParent().setVersion( newValue );
                        break;
                    }
                }

                // Apply any explicit overrides to the top level parent. Convert it to a simulated
                // dependency so we can reuse applyExplicitOverrides.
                final Dependency d = new Dependency();
                d.setGroupId( project.getModelParent().getGroupId() );
                d.setArtifactId( project.getModelParent().getArtifactId() );
                d.setVersion( project.getModelParent().getVersion() );
                final Map<ArtifactRef, Dependency> pDepMap =
                        Collections.singletonMap( new SimpleScopedArtifactRef( d ), d ) ;
                applyExplicitOverrides( project, pDepMap, explicitOverrides, explicitVersionPropertyUpdateMap );
                project.getModelParent().setVersion( d.getVersion() );
            }

            // Apply overrides to project dependency management
            logger.debug( "Applying overrides to managed dependencies for: {}", projectGA );

            final Map<ArtifactRef, String> nonMatchingVersionOverrides =
                            applyOverrides( project, project.getResolvedManagedDependencies( session ),
                                            explicitOverrides, originalOverrides );

            applyExplicitOverrides( project, project.getResolvedManagedDependencies( session ), explicitOverrides,
                                    explicitVersionPropertyUpdateMap );

            if ( commonState.isOverrideTransitive() && dependencyState.getRemoteBOMDepMgmt() != null )
            {
                final Collection<ArtifactRef> overrideRefs = overrides.keySet();
                final Collection<Dependency> extraDeps = new ArrayList<>( overrideRefs.size() );

                // Add dependencies to Dependency Management which did not match any existing dependency
                for ( final ArtifactRef pvr : overrideRefs )
                {
                    if ( !nonMatchingVersionOverrides.containsKey( pvr ) )
                    {
                        // This one in the remote pom was already dealt with ; continue.
                        continue;
                    }

                    final Dependency newDependency = new Dependency();
                    newDependency.setGroupId( pvr.getGroupId() );
                    newDependency.setArtifactId( pvr.getArtifactId() );
                    newDependency.setType( pvr.getType() );
                    newDependency.setClassifier( pvr.getClassifier() );

                    final String artifactVersion = originalOverrides.get( pvr );
                    newDependency.setVersion( artifactVersion );

                    extraDeps.add( newDependency );
                    logger.debug( "New entry added to <dependencyManagement/> - {} : {}", pvr, artifactVersion );
                }

                // If the model doesn't have any Dependency Management set by default, create one for it
                DependencyManagement dependencyManagement = model.getDependencyManagement();

                if ( !extraDeps.isEmpty() )
                {
                    if ( dependencyManagement == null )
                    {
                        dependencyManagement = new DependencyManagement();
                        model.setDependencyManagement( dependencyManagement );
                        logger.debug( "Added <DependencyManagement/> for current project" );
                    }
                    dependencyManagement.getDependencies().addAll( 0, extraDeps );
                }
            }
            else if ( commonState.isOverrideTransitive() && dependencyState.getRemoteBOMDepMgmt() == null )
            {
                logger.warn( "Ignoring {} flag since it was used without the {} option",
                        CommonState.TRANSITIVE_OVERRIDE_PROPERTY,
                        DependencyState.DEPENDENCY_MANAGEMENT_POM_PROPERTY );
            }
            else
            {
                logger.debug( "Non-matching dependencies ignored." );
            }
        }
        else
        {
            logger.debug( "Applying overrides to managed dependencies for: {}", projectGA );
            applyOverrides( project, project.getResolvedManagedDependencies( session ), explicitOverrides,
                            originalOverrides );
            applyExplicitOverrides( project, project.getResolvedManagedDependencies( session ), explicitOverrides,
                                    explicitVersionPropertyUpdateMap );
        }

        logger.debug( "Applying overrides to concrete dependencies for: {}", projectGA );
        // Apply overrides to project direct dependencies
        applyOverrides( project, project.getResolvedDependencies( session ), explicitOverrides, originalOverrides );
        applyExplicitOverrides( project, project.getResolvedDependencies( session ), explicitOverrides,
                                explicitVersionPropertyUpdateMap );

        final Map<Profile, Map<ArtifactRef, Dependency>> pd = project.getResolvedProfileDependencies( session );
        final Map<Profile, Map<ArtifactRef, Dependency>> pmd = project.getResolvedProfileManagedDependencies( session );

        for ( final Map<ArtifactRef, Dependency> dependencies : pd.values() )
        {
            applyOverrides( project, dependencies, explicitOverrides, originalOverrides );
            applyExplicitOverrides( project, dependencies, explicitOverrides, explicitVersionPropertyUpdateMap );
        }

        for ( final Map<ArtifactRef, Dependency> dependencies : pmd.values() )
        {
            applyOverrides( project, dependencies, explicitOverrides, originalOverrides );
            applyExplicitOverrides( project, dependencies, explicitOverrides, explicitVersionPropertyUpdateMap );
        }

        // Apply dependency changes to dependencies that occur within plugins.
        final  Map<ProjectVersionRef, Plugin> resolvedPlugins = project.getAllResolvedPlugins( session );
        applyPlugins( project, resolvedPlugins, explicitOverrides, originalOverrides);
        applyExplicitOverrides( project, resolvedPlugins, explicitOverrides, explicitVersionPropertyUpdateMap );

        final  Map<ProjectVersionRef, Plugin> resolvedManagedPlugins = project.getResolvedManagedPlugins( session );
        applyPlugins( project, resolvedManagedPlugins, explicitOverrides, originalOverrides);
        applyExplicitOverrides( project, resolvedManagedPlugins, explicitOverrides, explicitVersionPropertyUpdateMap );

        for (Map<ProjectVersionRef, Plugin> resolvedProfilePlugins : project.getAllResolvedProfilePlugins( session ).values() )
        {
            applyPlugins( project, resolvedProfilePlugins, explicitOverrides, originalOverrides);
            applyExplicitOverrides( project, resolvedProfilePlugins, explicitOverrides, explicitVersionPropertyUpdateMap );
        }
        for (Map<ProjectVersionRef, Plugin> resolvedManagedProfilePlugins : project.getResolvedProfileManagedPlugins( session ).values() )
        {
            applyPlugins( project, resolvedManagedProfilePlugins, explicitOverrides, originalOverrides);
            applyExplicitOverrides( project, resolvedManagedProfilePlugins, explicitOverrides, explicitVersionPropertyUpdateMap );
        }

        // This handles dependencies of plugins themselves.
        final List<Map<ArtifactRef, Dependency>> pluginDependencies = project.getAllResolvedPluginDependencies( session );
        for (Map<ArtifactRef, Dependency> depMap : pluginDependencies)
        {
            applyOverrides( project, depMap, explicitOverrides, originalOverrides );
            applyExplicitOverrides( project, depMap, explicitOverrides, explicitVersionPropertyUpdateMap );
        }
    }

    private void applyPlugins( Project project, Map<ProjectVersionRef, Plugin> plugins,
                               WildcardMap<String> explicitOverrides, Map<ArtifactRef, String> overrides )
                    throws ManipulationException
    {
        // Handles plugin configurations
        final List<PluginReference> refs = DependencyPluginUtils.findPluginReferences( galleyWrapper, project, plugins );

        // We need to create a map of PVR to PluginReference (which need to extend InputLocationTracker)
        // Use PropertyResolver to resolve the version (if it exists) inside PluginReference (ignore if doesn't)
        final Map<ProjectVersionRef, InputLocationTracker> pluginsWithDeps = new HashMap<>();
        for ( PluginReference reference : refs )
        {
            if (reference.getVersion() != null)
            {
                String resolvedVersion =
                                PropertyResolver.resolveInheritedProperties( session, project, reference.getVersion() );
                ProjectVersionRef resolvedRef = new SimpleProjectVersionRef( reference.getGroupId(), reference.getArtifactId(),
                                                                             resolvedVersion );
                pluginsWithDeps.put( resolvedRef, reference );
            }
        }
        logger.debug( "Located plugins with resolved artifact references: {}", pluginsWithDeps );

        // Reuse prior apply* to handle plugin with dependencies as well.
        applyOverrides( project, pluginsWithDeps, explicitOverrides, overrides );
        applyExplicitOverrides( project, pluginsWithDeps, explicitOverrides, explicitVersionPropertyUpdateMap );
    }

    /**
     * Apply a set of version overrides to a list of dependencies. Return a set of the overrides which were not applied.
     *
     * @param project The current Project
     * @param dependencies The list of dependencies
     * @param explicitOverrides Any explicitOverrides to track for ignoring
     * @param overrides The map of dependency version overrides
     * @return The map of overrides that were not matched in the dependencies
     * @throws ManipulationException if an error occurs
     */
    private Map<ArtifactRef, String> applyOverrides( final Project project,
                                                     final Map<? extends ProjectVersionRef, ? extends InputLocationTracker> dependencies,
                                                     final WildcardMap<String> explicitOverrides, final Map<ArtifactRef, String> overrides )
                    throws ManipulationException
    {
        // Duplicate the override map so unused overrides can be easily recorded
        final Map<ArtifactRef, String> unmatchedVersionOverrides = new LinkedHashMap<>( overrides );

        if ( dependencies == null || dependencies.isEmpty() )
        {
            return unmatchedVersionOverrides;
        }

        final CommonState commonState = session.getState( CommonState.class );
        final boolean strict = commonState.isStrict();

        // Apply matching overrides to dependencies
        for ( final Entry<? extends ProjectVersionRef, ? extends InputLocationTracker> e : dependencies.entrySet() )
        {
            final ProjectVersionRef dependency = e.getKey();
            ProjectRef depPr = new SimpleProjectRef( dependency.getGroupId(), dependency.getArtifactId() );

            // We might have junit:junit:3.8.2 and junit:junit:4.1 for differing override scenarios within the
            // overrides list. If strict mode alignment is enabled, using multiple overrides will work with
            // different modules. It is currently undefined what will happen if non-strict mode is enabled and
            // multiple versions are in the remote override list (be it from a bom or rest call). Actually, what
            // will most likely happen is last-wins.
            for ( final Entry<ArtifactRef, String> entry : overrides.entrySet() )
            {
                ProjectRef groupIdArtifactId = entry.getKey().asProjectRef();
                if ( depPr.equals( groupIdArtifactId ) )
                {
                    final DependencyPluginWrapper wrapper = new DependencyPluginWrapper( e.getValue() );
                    final String oldVersion = wrapper.getVersion();
                    final String overrideVersion = entry.getValue();
                    final String resolvedValue = dependency.getVersionString();

                    if ( isEmpty( overrideVersion ) )
                    {
                        logger.warn( "Unable to align with an empty override version for {}; ignoring", groupIdArtifactId );
                    }
                    else if ( isEmpty( oldVersion ) )
                    {
                        logger.debug( "Dependency is a managed version for {}; ignoring", groupIdArtifactId );
                    }
                    else if (oldVersion.equals( Version.PROJECT_VERSION ) || ( oldVersion.contains( "$" ) && project.getVersion().equals( resolvedValue ) ) )
                    {
                        logger.debug( "Dependency {} with original version {} and project version {} for {} references ${project.version} so skipping.",
                                     dependency, oldVersion, project.getVersion(), project.getPom() );
                    }
                    // If we have an explicitOverride, this will always override the dependency changes made here.
                    // By avoiding the potential duplicate work it also avoids a possible property clash problem.
                    else if ( explicitOverrides.containsKey( depPr ) )
                    {
                        logger.debug ("Dependency {} matches known explicit override so not performing initial override pass.", depPr);
                        unmatchedVersionOverrides.remove( entry.getKey() );
                    }
                    // If we're doing strict matching with properties, then the original parts should match.
                    // i.e. assuming original resolved value is 1.2 and potential new value is 1.2.rebuild-1
                    // then this is fine to continue. If the original is 1.2 and potential new value is 1.3.rebuild-1
                    // then don't bother to attempt to cache the property as the strict check would fail.
                    // This extra check avoids an erroneous "Property replacement clash" error.

                    // Can't blindly compare resolvedValue [original] against ar as ar / overrideVersion is the new GAV. We don't
                    // have immediate access to the original property so the closest that is feasible is verify strict matching.
                    else if ( strict && oldVersion.contains( "$" ) &&
                                    ! PropertiesUtils.checkStrictValue( session, resolvedValue, overrideVersion) )
                    {
                        logger.debug ("Original fully resolved version {} for {} does not match override version {} -> {} so ignoring",
                                      resolvedValue, dependency, entry.getKey(), overrideVersion);
                        if ( commonState.isFailOnStrictViolation() )
                        {
                            throw new ManipulationException(
                                            "For {} replacing original property version {} (fully resolved: {} ) with new version {} for {} violates the strict version-alignment rule!",
                                            depPr.toString(), wrapper.getVersion(), resolvedValue, entry.getKey().getVersionString(), entry.getKey().asProjectRef().toString());
                        }
                        else
                        {
                            logger.warn( "Replacing original property version {} with new version {} for {} violates the strict version-alignment rule!",
                                         resolvedValue, overrideVersion, wrapper.getVersion() );
                        }
                    }
                    else
                    {
                        if ( ! PropertiesUtils.cacheProperty( session, project, versionPropertyUpdateMap, oldVersion, overrideVersion, entry.getKey(), false ))
                        {
                            if ( strict && ! PropertiesUtils.checkStrictValue( session, resolvedValue, overrideVersion) )
                            {
                                if ( commonState.isFailOnStrictViolation() )
                                {
                                    throw new ManipulationException(
                                                     "Replacing original version {} in dependency {} with new version {} violates the strict version-alignment rule!",
                                                     oldVersion, groupIdArtifactId, overrideVersion );
                                }
                                else
                                {
                                    logger.warn( "Replacing original version {} in dependency {} with new version {} violates the strict version-alignment rule!",
                                                 oldVersion, groupIdArtifactId, overrideVersion );
                                }
                            }
                            else
                            {
                                logger.debug( "Altered dependency {} : {} -> {}", groupIdArtifactId, oldVersion,
                                              overrideVersion );

                                // This block handles a version that is a partial property with a value.
                                if ( oldVersion.contains( "${" ) )
                                {
                                    String suffix = PropertiesUtils.getSuffix( session );
                                    String replaceVersion;

                                    // Handles ${...}...-rebuild-n -> ${...}...-rebuild-n+1
                                    if ( commonState.isStrictIgnoreSuffix() && oldVersion.contains( suffix ) )
                                    {
                                        replaceVersion = StringUtils.substringBefore( oldVersion, suffix );
                                        replaceVersion += suffix + StringUtils.substringAfter( overrideVersion, suffix );
                                    }
                                    else
                                    {
                                        // It is feasible that even though ${foo}.x-suffix may look appropriate, foo may have
                                        // been updated by another dependency to foo-suffix so that we end up with
                                        // foo-suffix.x-suffix. Therefore just replace with overrideVersion
                                        replaceVersion = overrideVersion;
                                    }
                                    logger.debug ( "Resolved value is {} and replacement version is {}", resolvedValue, replaceVersion );

                                    // In this case the previous value couldn't be cached even though it contained a property
                                    // as it was either multiple properties or a property combined with a hardcoded value. Therefore
                                    // just append the suffix.
                                    wrapper.setVersion( replaceVersion );
                                }
                                else
                                {
                                    wrapper.setVersion( overrideVersion );
                                }
                            }
                        }
                        unmatchedVersionOverrides.remove( entry.getKey() );
                    }
                }
            }
        }

        return unmatchedVersionOverrides;
    }

    /**
     * Remove version overrides which refer to projects in the current reactor.
     * Projects in the reactor include things like inter-module dependencies
     * which should never be overridden.
     *
     * @param versionOverrides current set of ArtifactRef:newVersion overrides.
     * @return A new Map with the reactor GAs removed.
     */
    private Map<ArtifactRef, String> removeReactorGAs( final Map<ArtifactRef, String> versionOverrides )
    {
        final Map<ArtifactRef, String> reducedVersionOverrides = new LinkedHashMap<>( versionOverrides );
        final Iterator<Entry<ArtifactRef, String>> it = reducedVersionOverrides.entrySet().iterator();
        while ( it.hasNext() )
        {
            final Entry<ArtifactRef, String> e = it.next();
            // Rather than iterating through all projects we find the first match to remove from the list. This then
            // handles the scenario where there is a badly defined project with duplicate GA in the list.
            if (session.getProjects().stream().anyMatch((p ->
                e.getKey().getGroupId().equals( p.getGroupId() )
                        && e.getKey().getArtifactId().equals( p.getArtifactId() )))) {
                it.remove();
            }
        }
        return reducedVersionOverrides;
    }

    private void validateDependenciesUpdatedProperty( CommonState cState, Project p, Map<ArtifactRef, Dependency> dependencies )
                    throws ManipulationException
    {
        for ( final Entry<ArtifactRef, Dependency> entry : dependencies.entrySet() )
        {
            final ArtifactRef pvr = entry.getKey();
            final Dependency dependency = entry.getValue();
            final String versionProperty = dependency.getVersion();

            if ( startsWith( versionProperty, "${" ) )
            {
                PropertiesUtils.verifyPropertyMapping( cState, p, versionPropertyUpdateMap, pvr,
                                                       PropertiesUtils.extractPropertyName( versionProperty ) );
            }
        }
    }
}
