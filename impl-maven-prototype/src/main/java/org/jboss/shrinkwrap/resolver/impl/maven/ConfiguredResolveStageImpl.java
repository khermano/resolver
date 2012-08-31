/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.shrinkwrap.resolver.impl.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.shrinkwrap.resolver.api.CoordinateParseException;
import org.jboss.shrinkwrap.resolver.api.maven.ConfiguredResolveStage;
import org.jboss.shrinkwrap.resolver.api.maven.InvalidConfigurationFileException;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolutionFilter;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolutionStrategy;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.dependency.ConfiguredDependencyDeclarationBuilder;
import org.jboss.shrinkwrap.resolver.api.maven.dependency.DependencyDeclaration;
import org.jboss.shrinkwrap.resolver.api.maven.dependency.exclusion.DependencyExclusionBuilderToConfiguredDependencyDeclarationBuilderBridge;
import org.jboss.shrinkwrap.resolver.impl.maven.convert.MavenConverter;
import org.jboss.shrinkwrap.resolver.impl.maven.dependency.ConfiguredDependencyDeclarationBuilderImpl;
import org.jboss.shrinkwrap.resolver.impl.maven.filter.ScopeFilter;
import org.jboss.shrinkwrap.resolver.impl.maven.strategy.AcceptScopesStrategy;
import org.jboss.shrinkwrap.resolver.impl.maven.strategy.CombinedStrategy;
import org.jboss.shrinkwrap.resolver.impl.maven.task.ConfigureSettingsTask;
import org.jboss.shrinkwrap.resolver.impl.maven.util.Validate;
import org.sonatype.aether.artifact.ArtifactTypeRegistry;

/**
 * Implementation of a {@link ConfiguredResolveStage}
 *
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 */
class ConfiguredResolveStageImpl
    extends
    AbstractResolveStageBase<ConfiguredDependencyDeclarationBuilder, DependencyExclusionBuilderToConfiguredDependencyDeclarationBuilderBridge, ConfiguredResolveStage>
    implements ConfiguredResolveStage {

    public ConfiguredResolveStageImpl(MavenWorkingSession session) {
        super(session);

        ArtifactTypeRegistry stereotypes = session.getArtifactTypeRegistry();

        Validate.stateNotNull(session.getModel(),
            "Could not spawn ConfiguredResolveStage. An effective POM must be resolved first.");

        // store all dependency information to be able to retrieve versions later
        if (session.getModel().getDependencyManagement() != null) {
            Set<DependencyDeclaration> pomDependencyMngmt = MavenConverter.fromDependencies(session.getModel()
                .getDependencyManagement().getDependencies(), stereotypes);
            session.getDependencyManagement().addAll(pomDependencyMngmt);
        }

        // store all of the <dependencies> into version management
        Set<DependencyDeclaration> pomDefinedDependencies = MavenConverter.fromDependencies(session.getModel()
            .getDependencies(), stereotypes);

        session.getDeclaredDependencies().addAll(pomDefinedDependencies);

    }

    @Override
    public MavenFormatStage importTestDependencies() {

        ScopeType[] scopes = new ScopeType[] { ScopeType.TEST };

        pushScopedDependencies(scopes);
        return importAnyDependencies(new AcceptScopesStrategy(scopes));
    }

    @Override
    public MavenFormatStage importTestDependencies(MavenResolutionStrategy strategy) throws IllegalArgumentException {

        Validate.notNull(strategy, "Specified strategy for importing test dependencies must not be null");

        ScopeType[] scopes = new ScopeType[] { ScopeType.TEST };

        pushScopedDependencies(scopes);
        return importAnyDependencies(new CombinedStrategy(strategy, new AcceptScopesStrategy(scopes)));
    }

    @Override
    public MavenFormatStage importDefinedDependencies() {

        ScopeType[] scopes = new ScopeType[] { ScopeType.COMPILE, ScopeType.IMPORT, ScopeType.RUNTIME, ScopeType.SYSTEM };

        pushScopedDependencies(scopes);
        return importAnyDependencies(new AcceptScopesStrategy(scopes));
    }

    @Override
    public MavenFormatStage importDefinedDependencies(MavenResolutionStrategy strategy) throws IllegalArgumentException {

        Validate.notNull(strategy, "Specified strategy for importing test dependencies must not be null");

        ScopeType[] scopes = new ScopeType[] { ScopeType.COMPILE, ScopeType.IMPORT, ScopeType.RUNTIME, ScopeType.SYSTEM };

        pushScopedDependencies(scopes);
        return importAnyDependencies(new CombinedStrategy(strategy, new AcceptScopesStrategy(scopes)));
    }

    @Override
    public ConfiguredDependencyDeclarationBuilder addDependency() {
        return new ConfiguredDependencyDeclarationBuilderImpl(session);
    }

    @Override
    public ConfiguredDependencyDeclarationBuilder addDependency(String coordinate) throws CoordinateParseException {
        return new ConfiguredDependencyDeclarationBuilderImpl(session).and(coordinate);
    }

    @Override
    public ConfiguredResolveStage configureSettings(File settingsXmlFile) throws IllegalArgumentException,
        InvalidConfigurationFileException {
        this.session = new ConfigureSettingsTask(settingsXmlFile).execute(session);
        return new ConfiguredResolveStageImpl(session);
    }

    @Override
    public ConfiguredResolveStage configureSettings(String pathToSettingsXmlFile) throws IllegalArgumentException,
        InvalidConfigurationFileException {
        this.session = new ConfigureSettingsTask(pathToSettingsXmlFile).execute(session);
        return new ConfiguredResolveStageImpl(session);
    }

    @Override
    public MavenStrategyStage resolve(String coordinate) throws IllegalArgumentException {
        return resolve(new ConfiguredDependencyDeclarationBuilderImpl(session), coordinate);
    }

    @Override
    public MavenStrategyStage resolve(String... coordinates) throws IllegalArgumentException {
        return resolve(new ConfiguredDependencyDeclarationBuilderImpl(session), coordinates);
    }

    @Override
    public MavenStrategyStage resolve(DependencyDeclaration coordinate) throws IllegalArgumentException {
        return resolve(new ConfiguredDependencyDeclarationBuilderImpl(session), coordinate);
    }

    @Override
    public MavenStrategyStage resolve(DependencyDeclaration... coordinates) throws IllegalArgumentException {
        return resolve(new ConfiguredDependencyDeclarationBuilderImpl(session), coordinates);
    }

    private MavenFormatStage importAnyDependencies(MavenResolutionStrategy strategy) {
        // resolve
        return new MavenStrategyStageImpl(session).using(strategy);

    }

    private void pushScopedDependencies(final ScopeType... scopes) {

        // Get all declared dependencies
        final List<DependencyDeclaration> dependencies = new ArrayList<DependencyDeclaration>(
            session.getDeclaredDependencies());
        // // And add *this* artifact too
        // final MavenWorkingSession session = this.getMavenWorkingSession();
        // final Model model = session.getModel();
        // final DependencyDeclaration thisDeclaration = new DependencyDeclarationImpl(model.getGroupId(),
        // model.getArtifactId(), PackagingType.fromPackagingType(model.getPackaging()), null, model.getVersion(),
        // ScopeType.COMPILE, false, new HashSet<DependencyExclusion>());
        // session.getDependencies().add(thisDeclaration);

        // Filter by scope
        final MavenResolutionFilter preResolutionFilter = new ScopeFilter(scopes);

        // For all declared dependencies which pass the filter, add 'em to the Set of dependencies to be resolved for
        // this request
        for (final DependencyDeclaration candidate : dependencies) {
            if (preResolutionFilter.accepts(candidate)) {
                session.getDependencies().add(candidate);
            }
        }
    }

}
