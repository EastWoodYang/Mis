package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.util.ConfigureUtil

class MisSource {

    String name
    String flavorName
    String microModuleName
    List<String> paths

    String groupId
    String artifactId
    String version

    Dependencies dependencies

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

    void dependencies(Closure closure) {
        dependencies = new Dependencies()
        ConfigureUtil.configure(closure, dependencies)
    }

    void sourceFilter(Closure closure) {
        this.sourceFilter = closure
    }

}