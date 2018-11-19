package com.eastwood.tools.plugins.mis.core.extension

import com.eastwood.tools.plugins.mis.core.SourceSet
import org.gradle.util.ConfigureUtil

class Publication {

    String name
    String sourceSetName
    String microModuleName
    File buildDir

    String project

    Map<String, SourceSet> sourceSets

    String groupId
    String artifactId
    String version

    Dependencies dependencies

    Closure sourceFilter

    boolean invalid
    boolean hit

    Publication(final String name) {
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

    void dependencies(Closure closure) {
        dependencies = new Dependencies()
        ConfigureUtil.configure(closure, dependencies)
    }

    void sourceFilter(Closure closure) {
        this.sourceFilter = closure
    }

}