package com.gradleware.tooling.toolingmodel;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.gradleware.tooling.toolingmodel.util.Maybe;
import com.gradleware.tooling.utils.ImmutableCollection;

import java.io.File;
import java.util.List;

/**
 * Provides basic information about the Gradle project and its hierarchy.
 */
public interface OmniGradleProjectStructure extends HierarchicalModel<OmniGradleProjectStructure> {

    /**
     * Returns the parent project of this project.
     *
     * @return the parent project, can be null
     */
    @Override
    OmniGradleProjectStructure getParent();

    /**
     * Returns the immediate child projects of this project.
     *
     * @return the immediate child projects of this project
     */
    @ImmutableCollection
    List<OmniGradleProjectStructure> getChildren();

    /**
     * Returns this project and all the nested child projects in its hierarchy.
     *
     * @return this project and all the nested child projects in its hierarchy
     */
    @Override
    @ImmutableCollection
    List<OmniGradleProjectStructure> getAll();

    /**
     * Returns all projects that match the given criteria.
     *
     * @param predicate the criteria to match
     * @return the matching projects
     */
    @Override
    @ImmutableCollection
    List<OmniGradleProjectStructure> filter(Predicate<? super OmniGradleProjectStructure> predicate);

    /**
     * Returns the first project that matches the given criteria, if any.
     *
     * @param predicate the criteria to match
     * @return the matching project, if any
     */
    @Override
    Optional<OmniGradleProjectStructure> tryFind(Predicate<? super OmniGradleProjectStructure> predicate);

    /**
     * Returns the name of this project. Note that the name is not a unique identifier for the project.
     *
     * @return the name of this project
     */
    String getName();

    /**
     * Returns the path of this project. The path can be used as a unique identifier for the project within a given build.
     *
     * @return the path of this project
     */
    Path getPath();

    /**
     * Returns the project directory of this project.
     *
     * @return the project directory
     */
    Maybe<File> getProjectDirectory();

}
