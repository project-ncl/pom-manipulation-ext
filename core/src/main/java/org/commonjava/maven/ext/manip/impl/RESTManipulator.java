/**
 * Copyright (C) 2012 Red Hat, Inc. (jcasey@redhat.com)
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
package org.commonjava.maven.ext.manip.impl;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;
import org.commonjava.maven.atlas.ident.DependencyScope;
import org.commonjava.maven.atlas.ident.ref.ArtifactRef;
import org.commonjava.maven.atlas.ident.ref.ProjectRef;
import org.commonjava.maven.atlas.ident.ref.ProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleProjectVersionRef;
import org.commonjava.maven.atlas.ident.ref.SimpleTypeAndClassifier;
import org.commonjava.maven.atlas.ident.version.VersionSpec;
import org.commonjava.maven.ext.manip.ManipulationException;
import org.commonjava.maven.ext.manip.ManipulationSession;
import org.commonjava.maven.ext.manip.model.Project;
import org.commonjava.maven.ext.manip.model.SimpleScopedArtifactRef;
import org.commonjava.maven.ext.manip.rest.exception.RestException;
import org.commonjava.maven.ext.manip.state.DependencyState;
import org.commonjava.maven.ext.manip.state.RESTState;
import org.commonjava.maven.ext.manip.state.VersioningState;
import org.commonjava.maven.ext.manip.util.PropertiesUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * This Manipulator runs first. It makes a REST call to an external service to loadRemoteOverrides the GAVs to align the project version
 * and dependencies to. It will prepopulate Project GA versions into the VersioningState in case the VersioningManipulator has been activated
 * and the remote overrides into the DependencyState for those as well.
 */
@Component( role = Manipulator.class, hint = "rest-manipulator" )
public class RESTManipulator implements Manipulator
{
    private static final Logger logger = LoggerFactory.getLogger( RESTManipulator.class );

    @Override
    public void init( final ManipulationSession session )
    {
        final Properties userProps = session.getUserProperties();
        RESTState state = new RESTState( userProps );
        session.setState( state );
    }

    /**
     * Prescans the Project to build up a list of Project GAs and also the various Dependencies.
     */
    @Override
    public void scan( final List<Project> projects, final ManipulationSession session )
                    throws ManipulationException
    {
        final RESTState state = session.getState( RESTState.class );

        if ( !session.isEnabled() || !state.isEnabled() )
        {
            logger.debug( getClass().getSimpleName() + ": Nothing to do!" );
            return;
        }

        Set<ArtifactRef> localDeps = establishDependencies( projects, null );

        final List<ProjectVersionRef> restParam = getRestParam(projects, session, localDeps);

        // Call the REST to populate the result.
        logger.debug ("Passing the following into the REST client api {} ", restParam);
        logger.info ("Calling REST client...");
        long start = System.nanoTime();
        Map<ProjectVersionRef, String> restResult;

        try
        {
            restResult = state.getVersionTranslator().translateVersions( restParam );
        }
        catch (RestException e)
        {
            printFinishTime( start );
            throw e;
        }
        printFinishTime( start );
        logger.debug ("REST Client returned {} ", restResult);

        // Parse the rest result for the project GAs and store them in versioning state for use
        // there by incremental suffix calculation.
        VersioningState vstate = session.getState(VersioningState.class);
        Map<ProjectRef, Set<String>> versionStates = new HashMap<>();
        for ( final Project p : projects )
        {
            ProjectRef projectKey = p.getKey().asProjectRef();
            // ".asProjectRef()" must be there, even if ProjectRef
            // is a supertype of ProjectVersionRef, equals does not respect this
            ProjectVersionRef restKey = tryOmitSnapshotFromVersion(vstate, p.getKey());
            if ( restResult.containsKey( restKey ) )
            {
                // Found part of the current project to store in Versioning State
                Set<String> versions = versionStates.get( projectKey );
                if (versions == null)
                {
                    versions = new HashSet<>();
                    versionStates.put( projectKey, versions );
                }
                versions.add( restResult.get( restKey ) );
            }
        }
        logger.info ("Added the following ProjectRef:Version from REST call into VersionState {}", versionStates);
        final VersioningState vs = session.getState( VersioningState.class );
        vs.setRESTMetadata (versionStates);

        final DependencyState ds = session.getState( DependencyState.class );
        final Map<ArtifactRef, String> overrides = new HashMap<>();

        // Convert the loaded remote ProjectVersionRefs to the original ArtifactRefs
        for (ArtifactRef artifactKey : localDeps )
        {
            ProjectVersionRef restKey = tryOmitSnapshotFromVersion(vstate, artifactKey);
            if (restResult.containsKey( restKey ))
            {
                overrides.put( artifactKey, restResult.get( restKey ));
            }
        }
        logger.debug( "Setting REST Overrides {} ", overrides );
        ds.setRemoteRESTOverrides( overrides );
    }

    private static ProjectVersionRef tryOmitSnapshotFromVersion(VersioningState vstate, ProjectVersionRef projectKey)
            throws ManipulationException {
        return vstate.preserveSnapshot() ? projectKey : omitSnapshotFromVersion(projectKey);
    }

    private static List<ProjectVersionRef> getRestParam(List<Project> projects, ManipulationSession session, Set<ArtifactRef> localDeps)
            throws ManipulationException
    {
        final List<ProjectVersionRef> restParam = new ArrayList<>();
        final VersioningState vstate = session.getState(VersioningState.class);
        for ( final Project project : projects )
        {
            // TODO: Check this : For the rest API I think we need to check every project GA not just inheritance root.
            restParam.add( tryOmitSnapshotFromVersion(vstate, project.getKey()) );
        }

        // Ok we now have a defined list of top level project plus a unique list of all possible dependencies.
        // Need to send that to the rest interface to get a translation.
        for ( ArtifactRef p : localDeps )
        {
            ProjectVersionRef ref = p.asProjectVersionRef();
            restParam.add(tryOmitSnapshotFromVersion(vstate, ref));
        }
        return restParam;
    }

    private static ProjectVersionRef omitSnapshotFromVersion(ProjectVersionRef original)
            throws ManipulationException
    {
        ProjectVersionRef res = original;
        if(original.isSnapshot()) {
            VersionSpec versionSpec = original.getVersionSpec();
            if(!versionSpec.isSingle())
                throw new ManipulationException("Version spec cannot be a range.");
            res = original.selectVersion(versionSpec.renderStandard().replace("-SNAPSHOT", ""), true);
        }
        return res;
    }

    /**
     * No-op in this case - any changes, if configured, would happen in Versioning or Dependency Manipulators.
     */
    @Override
    public Set<Project> applyChanges( final List<Project> projects, final ManipulationSession session )
                    throws ManipulationException
    {
        return Collections.emptySet();
    }

    @Override
    public int getExecutionIndex()
    {
        // Low value index so it runs first in order to call the REST API.
        return 5;
    }


    /**
     * Scans a list of projects and accumulates all dependencies and returns them.
     * @param projects the projects to scan.
     * @param activeProfiles
     * @return an unsorted set of ArtifactRefs used.
     * @throws ManipulationException
     */
    public static Set<ArtifactRef> establishDependencies( final List<Project> projects, Set<String> activeProfiles ) throws ManipulationException
    {
        Set<ArtifactRef> localDeps = new TreeSet<>();
        Set<String> activeModules = new HashSet<>();
        boolean scanAll = false;

        if (activeProfiles != null && !activeProfiles.isEmpty())
        {
            for ( final Project project : projects )
            {
                if ( project.isInheritanceRoot() )
                {
                    activeModules.addAll( project.getModel().getModules() );

                    List<Profile> profiles = project.getModel().getProfiles();

                    if ( profiles != null )
                    {
                        for ( Profile p : profiles )
                        {
                            if (activeProfiles.contains( p.getId() ) )
                            {
                                activeModules.addAll( p.getModules() );
                            }
                        }
                    }
                }
            }
            logger.debug ("Found {} active modules with {} active profiles.", activeModules, activeProfiles);
        }
        else
        {
            scanAll = true;
        }

        // Iterate over current project set and populate list of dependencies
        for ( final Project project : projects )
        {
            if ( project.isInheritanceRoot() || scanAll || activeModules.contains( project.getPom().getParentFile().getName() ) )
            {
                recordDependencies( projects, project, localDeps, project.getManagedDependencies() );
                recordDependencies( projects, project, localDeps, project.getDependencies() );

                List<Profile> profiles = project.getModel().getProfiles();
                if ( profiles != null )
                {
                    for ( Profile p : profiles )
                    {
                        if ( !scanAll && !activeProfiles.contains( p.getId() ) )
                        {
                            continue;
                        }
                        if ( p.getDependencyManagement() != null )
                        {
                            recordDependencies( projects, project, localDeps, p.getDependencyManagement().getDependencies() );
                        }
                        recordDependencies( projects, project, localDeps, p.getDependencies() );
                    }
                }
            }

        }

        return localDeps;
    }


    /**
     * Translate a given set of dependencies into ProjectVersionRefs.
     * @param projects list of all projects
     * @param project currently scanned project
     * @param deps Set of ProjectVersionRef to store the results in.
     * @param dependencies dependencies to examine
     */
    private static void recordDependencies( List<Project> projects, Project project, Set<ArtifactRef> deps, Iterable<Dependency> dependencies )
                    throws ManipulationException
    {
        if ( dependencies == null )
        {
            return;
        }

        Iterator<Dependency> iterator = dependencies.iterator();

        while ( iterator.hasNext() )
        {
            Dependency d = iterator.next();

            if ( d.getVersion() == null )
            {
                logger.trace( "Skipping dependency " + d + " as empty version." );
            }
            else
            {
                deps.add( new SimpleScopedArtifactRef( new SimpleProjectVersionRef(
                                d.getGroupId().equals( "${project.groupId}" ) ? project.getGroupId() : d.getGroupId(),
                                d.getArtifactId().equals( "${project.artifactId}" ) ? project.getArtifactId() : d.getArtifactId(),
                                PropertiesUtils.resolveProperties( projects, d.getVersion() ) ),
                                                       new SimpleTypeAndClassifier( d.getType(), d.getClassifier() ),
                                                       // TODO: Should atlas handle default scope?
                                                       d.getScope() == null ? DependencyScope.compile.realName() : d.getScope()));
            }
        }
    }


    private void printFinishTime(long start)
    {
        long finish = System.nanoTime();
        long minutes = TimeUnit.NANOSECONDS.toMinutes( finish - start );
        long seconds = TimeUnit.NANOSECONDS.toSeconds( finish - start ) - ( minutes * 60 );
        logger.info ( "REST client finished... (took {} min, {} sec, {} millisec)", minutes, seconds,
                      (TimeUnit.NANOSECONDS.toMillis( finish - start ) - ( minutes * 60 * 1000 ) - ( seconds * 1000) ));
    }
}
