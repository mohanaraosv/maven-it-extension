package com.soebes.itf.maven.plugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Copy resources from src/test/resources-its to the appropriate location.
 * TODO: More information here!!
 *
 * @implNote Copied larger parts from maven-resources-plugin.
 */
@Mojo( name = "resources", defaultPhase = LifecyclePhase.PROCESS_TEST_RESOURCES, requiresProject = true, threadSafe = true )
public class ResourcesMojo
    extends AbstractMojo
    implements Contextualizable
{

    /**
     * The character encoding to use when reading and writing filtered resources.
     */
    @Parameter( defaultValue = "${project.build.sourceEncoding}" )
    protected String encoding;

    /**
     * The character encoding to use when reading and writing filtered properties files.
     * If not specified, it will default to the value of the "encoding" parameter.
     *
     */
    @Parameter
    protected String propertiesEncoding;

    /**
     * The output directory into which to copy the resources.
     */
    @Parameter( defaultValue = "${project.build.outputDirectory}", required = true )
    private File outputDirectory;

    /**
     * The list of resources we want to transfer.
     */
    @Parameter( defaultValue = "${project.resources}", required = true, readonly = true )
    private List<Resource> resources;

    /**
     *
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    protected MavenProject project;

    /**
     * The list of additional filter properties files to be used along with System and project properties, which would
     * be used for the filtering.
     * @see ResourcesMojo#filters
     *
     * @since 2.4
     */
    @Parameter( defaultValue = "${project.build.filters}", readonly = true )
    protected List<String> buildFilters;

    /**
     * <p>
     * The list of extra filter properties files to be used along with System properties, project properties, and filter
     * properties files specified in the POM build/filters section, which should be used for the filtering during the
     * current mojo execution.</p>
     * <p>
     * Normally, these will be configured from a plugin's execution section, to provide a different set of filters for a
     * particular execution. For instance, starting in Maven 2.2.0, you have the option of configuring executions with
     * the id's <code>default-resources</code> and <code>default-testResources</code> to supply different configurations
     * for the two different types of resources. By supplying <code>extraFilters</code> configurations, you can separate
     * which filters are used for which type of resource.</p>
     */
    @Parameter
    protected List<String> filters;

    /**
     * If false, don't use the filters specified in the build/filters section of the POM when processing resources in
     * this mojo execution.
     * @see ResourcesMojo#buildFilters 
     * @see ResourcesMojo#filters
     *
     * @since 2.4
     */
    @Parameter( defaultValue = "true" )
    protected boolean useBuildFilters;

    /**
     *
     */
    @Component( role = MavenResourcesFiltering.class, hint = "default" )
    protected MavenResourcesFiltering mavenResourcesFiltering;

    /**
     *
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected MavenSession session;

    /**
     * Expressions preceded with this string won't be interpolated. Anything else preceded with this string will be
     * passed through unchanged. For example {@code \${foo}} will be replaced with {@code ${foo}} but {@code \\${foo}}
     * will be replaced with {@code \\value of foo}, if this parameter has been set to the backslash.
     * @since 2.3
     */
    @Parameter
    protected String escapeString;

    /**
     * Overwrite existing files even if the destination files are newer.
     *
     */
    @Parameter( defaultValue = "false" )
    private boolean overwrite;

    /**
     * Copy any empty directories included in the Resources.
     */
    @Parameter( defaultValue = "false" )
    protected boolean includeEmptyDirs;

    /**
     * Additional file extensions to not apply filtering (already defined are : jpg, jpeg, gif, bmp, png)
     *
     */
    @Parameter
    protected List<String> nonFilteredFileExtensions;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     *
     */
    @Parameter( defaultValue = "true" )
    protected boolean escapeWindowsPaths;

    /**
     * <p>
     * Set of delimiters for expressions to filter within the resources. These delimiters are specified in the form
     * {@code beginToken*endToken}. If no {@code *} is given, the delimiter is assumed to be the same for start and end.
     * </p>
     * <p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     * 
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;@&lt;/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * <p>
     * Since the {@code @} delimiter is the same on both ends, we don't need to specify {@code @*@} (though we can).
     * </p>
     *
     * @since 2.4
     */
    @Parameter
    protected LinkedHashSet<String> delimiters;

    /**
     * Use default delimiters in addition to custom delimiters, if any.
     */
    @Parameter( defaultValue = "true" )
    protected boolean useDefaultDelimiters;

    /**
     * By default files like {@code .gitignore}, {@code .cvsignore} etc. are excluded which means they will not being
     * copied. If you need them for a particular reason you can do that by settings this to {@code false}. This means
     * all files like the following will be copied.
     * <ul>
     * <li>Misc: &#42;&#42;/&#42;~, &#42;&#42;/#&#42;#, &#42;&#42;/.#&#42;, &#42;&#42;/%&#42;%, &#42;&#42;/._&#42;</li>
     * <li>CVS: &#42;&#42;/CVS, &#42;&#42;/CVS/&#42;&#42;, &#42;&#42;/.cvsignore</li>
     * <li>RCS: &#42;&#42;/RCS, &#42;&#42;/RCS/&#42;&#42;</li>
     * <li>SCCS: &#42;&#42;/SCCS, &#42;&#42;/SCCS/&#42;&#42;</li>
     * <li>VSSercer: &#42;&#42;/vssver.scc</li>
     * <li>MKS: &#42;&#42;/project.pj</li>
     * <li>SVN: &#42;&#42;/.svn, &#42;&#42;/.svn/&#42;&#42;</li>
     * <li>GNU: &#42;&#42;/.arch-ids, &#42;&#42;/.arch-ids/&#42;&#42;</li>
     * <li>Bazaar: &#42;&#42;/.bzr, &#42;&#42;/.bzr/&#42;&#42;</li>
     * <li>SurroundSCM: &#42;&#42;/.MySCMServerInfo</li>
     * <li>Mac: &#42;&#42;/.DS_Store</li>
     * <li>Serena Dimension: &#42;&#42;/.metadata, &#42;&#42;/.metadata/&#42;&#42;</li>
     * <li>Mercurial: &#42;&#42;/.hg, &#42;&#42;/.hg/&#42;&#42;</li>
     * <li>GIT: &#42;&#42;/.git, &#42;&#42;/.gitignore, &#42;&#42;/.gitattributes, &#42;&#42;/.git/&#42;&#42;</li>
     * <li>Bitkeeper: &#42;&#42;/BitKeeper, &#42;&#42;/BitKeeper/&#42;&#42;, &#42;&#42;/ChangeSet,
     * &#42;&#42;/ChangeSet/&#42;&#42;</li>
     * <li>Darcs: &#42;&#42;/_darcs, &#42;&#42;/_darcs/&#42;&#42;, &#42;&#42;/.darcsrepo,
     * &#42;&#42;/.darcsrepo/&#42;&#42;&#42;&#42;/-darcs-backup&#42;, &#42;&#42;/.darcs-temp-mail
     * </ul>
     *
     * @since 3.0.0
     */
    @Parameter( defaultValue = "true" )
    protected boolean addDefaultExcludes;

    /**
     * <p>
     * List of plexus components hint which implements
     * {@link MavenResourcesFiltering#filterResources(MavenResourcesExecution)}. They will be executed after the
     * resources copying/filtering.
     * </p>
     *
     * @since 2.4
     */
    @Parameter
    private List<String> mavenFilteringHints;

    /**
     * @since 2.4
     */
    private PlexusContainer plexusContainer;

    /**
     * @since 2.4
     */
    private List<MavenResourcesFiltering> mavenFilteringComponents = new ArrayList<MavenResourcesFiltering>();

    /**
     * stop searching endToken at the end of line
     *
     * @since 2.5
     */
    @Parameter( defaultValue = "false" )
    private boolean supportMultiLineFiltering;

    /**
     * Support filtering of filenames folders etc.
     * 
     * @since 3.0.0
     */
    @Parameter( defaultValue = "false" )
    private boolean fileNameFiltering;

    /** {@inheritDoc} */
    public void contextualize( Context context )
        throws ContextException
    {
        plexusContainer = (PlexusContainer) context.get( PlexusConstants.PLEXUS_KEY );
    }

    /** {@inheritDoc} */
    public void execute()
        throws MojoExecutionException
    {
        if ( Helper.isBlank( encoding ) && isFilteringEnabled( this.resources ) )
        {
            getLog().warn( "File encoding has not been set, using platform encoding "
                + System.getProperty( "file.encoding" )
                + ". Build is platform dependent!" );
            getLog().warn( "See https://maven.apache.org/general.html#encoding-warning" );
        }

        try
        {
            List<String> combinedFilters = getCombinedFiltersList();

            MavenResourcesExecution mavenResourcesExecution =
                new MavenResourcesExecution( this.resources, this.outputDirectory, project, encoding, combinedFilters,
                                             Collections.<String>emptyList(), session );

            mavenResourcesExecution.setEscapeWindowsPaths( escapeWindowsPaths );

            // never include project build filters in this call, since we've already accounted for the POM build filters
            // above, in getCombinedFiltersList().
            mavenResourcesExecution.setInjectProjectBuildFilters( false );

            mavenResourcesExecution.setEscapeString( escapeString );
            mavenResourcesExecution.setOverwrite( overwrite );
            mavenResourcesExecution.setIncludeEmptyDirs( includeEmptyDirs );
            mavenResourcesExecution.setSupportMultiLineFiltering( supportMultiLineFiltering );
            mavenResourcesExecution.setFilterFilenames( fileNameFiltering );
            mavenResourcesExecution.setAddDefaultExcludes( addDefaultExcludes );

            // We don't define supplemental properties at the moment.
            mavenResourcesExecution.setAdditionalProperties( null );

            // if these are NOT set, just use the defaults, which are '${*}' and '@'.
            mavenResourcesExecution.setDelimiters( delimiters, useDefaultDelimiters );

            mavenResourcesExecution.setPropertiesEncoding( propertiesEncoding );

            if ( nonFilteredFileExtensions != null )
            {
                mavenResourcesExecution.setNonFilteredFileExtensions( nonFilteredFileExtensions );
            }
            mavenResourcesFiltering.filterResources( mavenResourcesExecution );

            executeUserFilterComponents( mavenResourcesExecution );
        }
        catch ( MavenFilteringException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    /**
     * @param mavenResourcesExecution {@link MavenResourcesExecution} 
     * @throws MojoExecutionException in case of wrong lookup.
     * @throws MavenFilteringException in case of failure.
     * @since 2.5
     */
    protected void executeUserFilterComponents( MavenResourcesExecution mavenResourcesExecution )
        throws MojoExecutionException, MavenFilteringException
    {

        if ( mavenFilteringHints != null )
        {
            for ( String hint : mavenFilteringHints )
            {
                try
                {
                    // CHECKSTYLE_OFF: LineLength
                    mavenFilteringComponents.add( (MavenResourcesFiltering) plexusContainer.lookup( MavenResourcesFiltering.class.getName(),
                                                                                                    hint ) );
                    // CHECKSTYLE_ON: LineLength
                }
                catch ( ComponentLookupException e )
                {
                    throw new MojoExecutionException( e.getMessage(), e );
                }
            }
        }
        else
        {
            getLog().debug( "no use filter components" );
        }

        if ( mavenFilteringComponents != null && !mavenFilteringComponents.isEmpty() )
        {
            getLog().debug( "execute user filters" );
            for ( MavenResourcesFiltering filter : mavenFilteringComponents )
            {
                filter.filterResources( mavenResourcesExecution );
            }
        }
    }

    /**
     * @return The combined filters.
     */
    protected List<String> getCombinedFiltersList()
    {
        if ( filters == null || filters.isEmpty() )
        {
            return useBuildFilters ? buildFilters : null;
        }
        else
        {
            List<String> result = new ArrayList<String>();

            if ( useBuildFilters && buildFilters != null && !buildFilters.isEmpty() )
            {
                result.addAll( buildFilters );
            }

            result.addAll( filters );

            return result;
        }
    }

    /**
     * Determines whether filtering has been enabled for any resource.
     *
     * @param theResources The set of resources to check for filtering, may be <code>null</code>.
     * @return <code>true</code> if at least one resource uses filtering, <code>false</code> otherwise.
     */
    private boolean isFilteringEnabled( Collection<Resource> theResources )
    {
        if ( theResources != null )
        {
            for ( Resource resource : theResources )
            {
                if ( resource.isFiltering() )
                {
                    return true;
                }
            }
        }
        return false;
    }

}
