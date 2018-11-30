package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

class MisExtension {

    Project project
    NamedDomainObjectContainer<Publication> publications
    Action<? super RepositoryHandler> configure

    MisExtension(Project project) {
        this.project = project
        this.publications = project.container(Publication)
    }

    void publications(Closure closure) {
        publications.configure(closure)
    }

    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

}
