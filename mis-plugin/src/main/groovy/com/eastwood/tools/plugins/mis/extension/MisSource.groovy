package com.eastwood.tools.plugins.mis.extension

import org.gradle.util.ConfigureUtil

class MisSource {

    String name
    String flavorName
    String microModuleName
    List<String> paths

    String groupId
    String artifactId
    String version

    String[] dependencies
    String[] compileOnly
    MavenRepository repository

    Closure sourceFilter

    boolean invalid

    MisSource(final String name) {
        this.name = name
    }

    void groupId(String groupId) {
        this.groupId = groupId
    }

    void artifactId(String artifactId) {
        this.artifactId = artifactId
    }

    void version(String version) {
        this.version = version
    }

    void dependencies(String... dependencies) {
        this.dependencies = dependencies
    }

    void compileOnly(String... compileOnly) {
        this.compileOnly = compileOnly
    }

    void repository(Closure closure) {
        repository = new MavenRepository()
        ConfigureUtil.configure(closure, repository)
    }

    void sourceFilter(Closure closure) {
        this.sourceFilter = closure
    }

    void invalid(boolean invalid) {
        this.invalid = invalid
    }

}