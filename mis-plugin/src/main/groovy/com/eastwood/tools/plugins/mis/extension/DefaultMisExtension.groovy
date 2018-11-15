package com.eastwood.tools.plugins.mis.extension

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class DefaultMisExtension implements MisExtension {

    Project project
    MavenRepository repository

    NamedDomainObjectContainer<MisSource> sourceSets

    OnMisSourceListener onMisSourceListener

    DefaultMisExtension(Project project, OnMisSourceListener listener) {
        this.project = project
        this.onMisSourceListener = listener
        this.repository = new MavenRepository()
        this.sourceSets = project.container(MisSource)
    }

    @Override
    void repository(Closure closure) {
        ConfigureUtil.configure(closure, repository)
    }

    @Override
    void sourceSets(Closure closure) {
        this.sourceSets.configure(closure)
        onMisSourceListener.onMisSourceSetsCreated(sourceSets)
    }

}