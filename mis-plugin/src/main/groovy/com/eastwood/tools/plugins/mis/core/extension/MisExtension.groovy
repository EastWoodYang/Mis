package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class MisExtension {

    Project project
    MavenRepository repository

    NamedDomainObjectContainer<MisSource> sourceSets

    OnMisSourceListener onMisSourceListener

    MisExtension(Project project, OnMisSourceListener listener) {
        this.project = project
        this.onMisSourceListener = listener
        this.repository = new MavenRepository()
        this.sourceSets = project.container(MisSource)
    }

    void repository(Closure closure) {
        ConfigureUtil.configure(closure, repository)
    }

    void sourceSets(Closure closure) {
        this.sourceSets.configure(closure)
        onMisSourceListener.onMisSourceSetsCreated(sourceSets)
    }

}