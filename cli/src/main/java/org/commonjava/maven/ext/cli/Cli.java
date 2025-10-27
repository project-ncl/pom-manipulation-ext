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
package org.commonjava.maven.ext.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import com.redhat.resilience.otel.OTelCLIHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulationException;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.profile.DefaultProfileActivationContext;
import org.apache.maven.model.profile.ProfileActivationContext;
import org.apache.maven.model.profile.ProfileSelector;
import org.apache.maven.settings.Profile;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.SettingsUtils;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingResult;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.commonjava.atlas.maven.ident.ref.ArtifactRef;
import org.commonjava.atlas.maven.ident.ref.InvalidRefException;
import org.commonjava.maven.ext.common.ManipulationException;
import org.commonjava.maven.ext.common.model.SimpleScopedArtifactRef;
import org.commonjava.maven.ext.core.ManipulationManager;
import org.commonjava.maven.ext.core.ManipulationSession;
import org.commonjava.maven.ext.core.impl.RESTCollector;
import org.commonjava.maven.ext.core.state.RESTState;
import org.commonjava.maven.ext.core.util.PropertiesUtils;
import org.commonjava.maven.ext.io.ConfigIO;
import org.commonjava.maven.ext.io.PomIO;
import org.commonjava.maven.ext.io.rest.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.commonjava.maven.ext.core.state.VersioningState.VERSION_SUFFIX_SNAPSHOT_SYSPROP;

@Command( name = "PME",
          description = "CLI to run PME",
          mixinStandardHelpOptions = true, // add --help and --version options
          exitCodeOnInvalidInput = 10,
          versionProvider = ManifestVersionProvider.class)
public class Cli implements Callable<Integer>
{
    private static final File DEFAULT_GLOBAL_SETTINGS_FILE =
        new File( System.getProperty( "maven.home" ), "conf" + File.separator + "settings.xml" );

    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private ManipulationSession session;

    private ManipulationManager manipulationManager;

    private PomIO pomIO;

    private PlexusContainer container;

    /**
     * Default pom file to operate against.
     */
    @SuppressWarnings("FieldMayBeFinal")
    @Option(names = {"-f", "--file"}, description = "POM File")
    private File target = new File( System.getProperty( "user.dir" ), "pom.xml" );

    /**
     * Optional settings.xml file.
     */
    @Option( names = {"-s", "--settings"}, description = "Optional settings.xml file")
    private File settings;

    /**
     * Optional logging file.
     */
    @SuppressWarnings("unused")
    @Option(names = {"--log"}, description = "Optional file to log output to")
    private String logFile;

    /**
     * Properties a user may define on the command line.
     */
    @Option( names = "-D", mapFallbackValue = "true", description = "Pass Java Properties (default value ${MAP-FALLBACK-VALUE})" )
    @SuppressWarnings( "unused" )
    private Properties userProps;

    @Option( names = {"-P", "--activeProfiles"}, description = "Comma separated list of active profiles.", split = ",")
    private final Set<String> profiles = new HashSet<>();

    @Option( names = { "-q", "--quiet"}, description = "Enable quiet logging (warn/error level)")
    boolean quiet;

    @Option( names = { "-d", "--debug"}, description = "Enable debug logging")
    boolean debug;

    @Option( names = { "-t", "--trace"}, description = "Enable trace logging")
    boolean trace;

    @Option( names = {"--printProjectDeps"}, description = "Print project dependencies")
    boolean printProjectDeps;

    @Option( names = {"--printManipulatorOrder"}, description = "Print current manipulator order")
    boolean printManipulatorOrder;

    public static void main( String[] args )
    {
        System.exit ( new Cli().run( args ) );
    }

    /**
     * Main method to invoke the cli tool. Note this is public as it may be called by external tools.
     * @param args ; an array of string arguments.
     * @return the exit code.
     */
    @SuppressWarnings("WeakerAccess") // Public API.
    public int run( String[] args ) {
        CommandLine cl = new CommandLine(this);
        cl.setUsageHelpAutoWidth(true);
        cl.setExecutionStrategy(new CommandLine.RunAll());
        cl.setOverwrittenOptionsAllowed(true);

        return cl.execute(args);
    }

    @Override
    public Integer call() {
        createSession( target, settings );

        final boolean runningInContainer = runningInContainer();
        final Logger rootLogger = LoggerFactory.getLogger( org.slf4j.Logger.ROOT_LOGGER_NAME );
        final ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) rootLogger;

        if ( logFile != null )
        {
            if (runningInContainer)
            {
                logger.error( "Disabling log file as running in container!" );
            }
            else
            {
                LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
                loggerContext.reset();

                PatternLayoutEncoder ple = new PatternLayoutEncoder();
                ple.setPattern( "%level %logger{36} %msg%n" );
                ple.setContext( loggerContext );
                ple.start();

                FileAppender<ILoggingEvent> fileAppender = new FileAppender<>();
                fileAppender.setEncoder( ple );
                fileAppender.setContext( loggerContext );
                fileAppender.setName( "fileLogging" );
                fileAppender.setAppend( false );
                fileAppender.setFile( logFile );
                fileAppender.start();

                root.addAppender( fileAppender );
                root.setLevel( Level.INFO );
            }
        }
        // Set debug logging after session creation else we get the log filled with Plexus
        // creation stuff.
        if (trace)
        {
            root.setLevel( Level.TRACE );
        }
        if (debug)
        {
            root.setLevel( Level.DEBUG );
        }
        if (quiet)
        {
            root.setLevel( Level.WARN );
        }

        if ( !session.isEnabled() )
        {
            logger.info( "Manipulation engine disabled via command-line option" );
            return 0;
        }
        // Don't bother skipping if we're just trying to analyse dependencies/print manipulator order.
        if ( new File( target.getParentFile(), ManipulationManager.MARKER_FILE ).exists() && !printManipulatorOrder && !printProjectDeps)
        {
            logger.info( "Skipping manipulation as previous execution found." );
            return 0;
        }
        try
        {
            PropertiesUtils.handleConfigPrecedence( session.getUserProperties(), new ConfigIO().parse( target.getParentFile() ) );
        }
        catch ( ManipulationException e )
        {
            logger.error( "POM Manipulation failed: Unable to read config file ", e );
            return 10;
        }

        try
        {
            // Note : don't print out settings information earlier (like when we actually read it) as the logging
            // isn't setup then.
            if ( logger.isDebugEnabled() )
            {
                logger.debug("Using local repository {}{} and found global settings file in {} with contents {}{} and user settings file in {} with contents {}{}",
                        System.lineSeparator(), session.getLocalRepository(), DEFAULT_GLOBAL_SETTINGS_FILE,
                        System.lineSeparator(),
                        DEFAULT_GLOBAL_SETTINGS_FILE.exists()
                                ? FileUtils.readFileToString( DEFAULT_GLOBAL_SETTINGS_FILE, StandardCharsets.UTF_8 )
                                : "** File does not exist **",
                        settings, System.lineSeparator(),
                        ( settings != null && settings.exists() )
                                ? FileUtils.readFileToString( settings, StandardCharsets.UTF_8 )
                                : "** File does not exist **"
                 );
            }
            String endpoint = System.getenv( "OTEL_EXPORTER_OTLP_ENDPOINT" );
            String service = System.getenv( "OTEL_SERVICE_NAME" );
            if ( endpoint != null )
            {
                if (service == null)
                {
                    service = "pom-manipulation-ext";
                }
                logger.info( "Enabling OpenTelemetry collection on {} with service name {}", endpoint, service );
                OTelCLIHelper.startOTel( service, "cli",
                                         OTelCLIHelper.defaultSpanProcessor( OTelCLIHelper.defaultSpanExporter( endpoint ) ) );
            }
            manipulationManager.init( session );

            if (!profiles.isEmpty())
            {
                session.getActiveProfiles().addAll( profiles );
                logger.debug ("Setting active profiles of {}", profiles);
            }
            else
            {
                logger.debug ("NOT activating any profiles.");
            }

            if (printProjectDeps)
            {
                // When just listing dependencies we don't want to strip any snapshot suffix.
                session.getUserProperties().put( VERSION_SUFFIX_SNAPSHOT_SYSPROP, "true");
                session.reinitialiseStates();
                List<ArtifactRef> ts = RESTCollector.establishAllDependencies( session, pomIO.parseProject( session.getPom() ),
                                                                              profiles ).stream().sorted().collect(Collectors.toList());
                System.out.format( "Found %d dependencies%n", ts.size() );
                System.out.format( "\u001B[32m%-80s%-20s%-20s%-20s\033[0m%n",
                                  StringUtils.center( "GAV", 60 ), "TYPE", "CLASSIFIER", "SCOPE" );

                for ( ArtifactRef a : ts )
                {
                    if ( a instanceof SimpleScopedArtifactRef )
                    {
                        final boolean isPlugin =  "maven-plugin".equals( a.getTypeAndClassifier().getType() );
                        final String scope = ( (SimpleScopedArtifactRef) a ).getScope() == null ? "compile" : ( (SimpleScopedArtifactRef) a ).getScope();
                        System.out.format("%-80s%-20s%-20s%-20s%n", a.asProjectVersionRef(),
                                          a.getTypeAndClassifier().getType(),
                                          a.getTypeAndClassifier().getClassifier() == null ? "" : a.getTypeAndClassifier().getClassifier(),
                                          isPlugin ? "" : scope);
                    }
                }
            }
            else if (printManipulatorOrder)
            {
                System.out.println ("\u001B[32mManipulator order is:\033[0m");
                manipulationManager.getOrderedManipulators().forEach
                        (m -> System.out.format( "%-20s%-40s%n",
                                                 StringUtils.center( String.valueOf( m.getExecutionIndex() ), 20),
                                                 m.getClass().getSimpleName()));
            }
            else
            {
                try
                {
                    manipulationManager.scanAndApply( session );
                }
                finally
                {
                    OTelCLIHelper.stopOTel();
                }
            }
        }
        catch ( RestException e )
        {
            logger.error ( "REST communication with {} failed. {}", userProps.getProperty( RESTState.REST_URL ), e.getMessage () );
            logger.trace ( "Exception trace is", e);
            return 100;
        }
        catch ( ManipulationException e )
        {
            logger.error( "POM Manipulation failed; original error is: {}", e.getMessage(), e );
            return 10;
        }
        catch ( InvalidRefException e )
        {
            logger.error( "POM Manipulation failed; original error is: {}", e.getMessage(), e );
            return 10;
        }
        catch ( Exception e )
        {
            logger.error( "POM Manipulation failed.", e );
            return 100;
        }
        return 0;
    }

    @SuppressWarnings( "deprecation" )
    private void createSession( File target, File settings )
    {
        try
        {
            final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
            config.setClassPathScanning( PlexusConstants.SCANNING_ON );
            config.setComponentVisibility( PlexusConstants.GLOBAL_VISIBILITY );
            config.setName( "PME-CLI" );
            container = new DefaultPlexusContainer(config);

            pomIO = container.lookup( PomIO.class );
            session = container.lookup( ManipulationSession.class );
            manipulationManager = container.lookup( ManipulationManager.class );

            final MavenExecutionRequest req = new DefaultMavenExecutionRequest().setSystemProperties( System.getProperties() )
                                                                                .setUserProperties( userProps )
                                                                                .setRemoteRepositories( Collections.emptyList() );

            ArtifactRepository ar = null;
            if ( settings == null )
            {
                File mavenHome = new File ( System.getProperty( "user.home" ), ".m2" );

                // No, this is not a typo. If current default is null, supply new local and global.
                // This function passes in settings to make it easier to test.
                this.settings = settings = new File( mavenHome, "settings.xml" );

                ar = new MavenArtifactRepository();
                ar.setUrl( Paths.get( mavenHome.getAbsolutePath(), "repository"  ).toUri().toString() );
                req.setLocalRepository( ar );
            }

            req.setUserSettingsFile( settings );
            req.setGlobalSettingsFile( settings );

            MavenExecutionRequestPopulator executionRequestPopulator = container.lookup( MavenExecutionRequestPopulator.class );

            executionRequestPopulator.populateFromSettings( req, parseSettings( settings ) );
            executionRequestPopulator.populateDefaults( req );

            if ( ar != null)
            {
                ar.setUrl( req.getLocalRepositoryPath().toURI().toString() );
            }

            if ( userProps != null && userProps.containsKey( "maven.repo.local" ) )
            {
                if ( ar == null)
                {
                    ar = new MavenArtifactRepository();
                }
                ar.setUrl( Paths.get( userProps.getProperty( "maven.repo.local" ) ).toUri().toString() );
                req.setLocalRepository( ar );
            }

            final MavenSession mavenSession = new MavenSession( container, null, req, new DefaultMavenExecutionResult() );

            mavenSession.getRequest().setPom( target );

            session.setMavenSession( mavenSession );
        }
        catch ( ComponentLookupException | PlexusContainerException e )
        {
            logger.debug( "Caught problem instantiating ", e );
            System.err.println( "Unable to start Cli subsystem" );
            System.exit( 100 );
        }
        catch ( SettingsBuildingException e )
        {
            logger.debug( "Caught problem parsing settings file ", e );
            System.err.println( "Unable to parse settings.xml file" );
            System.exit( 100 );
        }
        catch ( MavenExecutionRequestPopulationException e )
        {
            logger.debug( "Caught problem populating maven request from settings file ", e );
            System.err.println( "Unable to create maven execution request from settings.xml file" );
            System.exit( 100 );
        }
    }

    private Settings parseSettings( File settings ) throws ComponentLookupException, SettingsBuildingException
    {
        DefaultSettingsBuildingRequest settingsRequest = new DefaultSettingsBuildingRequest();
        settingsRequest.setUserSettingsFile( settings );
        settingsRequest.setGlobalSettingsFile( DEFAULT_GLOBAL_SETTINGS_FILE );
        settingsRequest.setUserProperties( session.getUserProperties() );
        settingsRequest.setSystemProperties( System.getProperties() );

        SettingsBuilder settingsBuilder = container.lookup( SettingsBuilder.class );
        SettingsBuildingResult settingsResult = settingsBuilder.build( settingsRequest );
        Settings effectiveSettings = settingsResult.getEffectiveSettings();

        ProfileSelector profileSelector = container.lookup( ProfileSelector.class );
        ProfileActivationContext profileActivationContext =
            new DefaultProfileActivationContext().setActiveProfileIds( effectiveSettings.getActiveProfiles() );
        List<org.apache.maven.model.Profile> modelProfiles = new ArrayList<>();
        for ( Profile profile : effectiveSettings.getProfiles() )
        {
            modelProfiles.add( SettingsUtils.convertFromSettingsProfile( profile ) );
        }
        List<org.apache.maven.model.Profile> activeModelProfiles =
            profileSelector.getActiveProfiles( modelProfiles, profileActivationContext,
                                               modelProblemCollectorRequest -> {
                                                   // do nothing
                                               } );
        List<String> activeProfiles = new ArrayList<>();
        for ( org.apache.maven.model.Profile profile : activeModelProfiles )
        {
            activeProfiles.add( profile.getId() );
        }
        effectiveSettings.setActiveProfiles( activeProfiles );

        return effectiveSettings;
    }

    /**
     * Determine whether the process is running inside an image.
     * See <a href="https://hackmd.io/gTlORH1KTuOuoWoAAzD42g">here</a>
     *
     * @return true if running in a container
     */
    private boolean runningInContainer()
    {
        final Path cgroup = Paths.get( getCGroups() );
        boolean result = false;

        if ( Files.isReadable( cgroup ) )
        {
            try ( Stream<String> stream = Files.lines( cgroup ) )
            {
                result = stream.anyMatch( line -> line.contains( "docker" ) || line.contains( "kubepods" ) );
            }
            catch ( Exception e )
            {
                logger.error( "Unable to determine if running in a container", e );
            }
        }

        if ( !result )
        {
            result = System.getenv().containsKey( "container" );
        }

        return result;
    }

    // Split into separate method for testing only.
    private static String getCGroups()
    {
        return "/proc/1/cgroup";
    }
}
