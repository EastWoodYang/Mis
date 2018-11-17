package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

class MisExtension {

    Project project
    NamedDomainObjectContainer<Publication> publications
    Action<? super RepositoryHandler> configure
    OnPublicationListener onPublicationListener

    MisExtension(Project project, OnPublicationListener listener) {
        this.project = project
        this.onPublicationListener = listener
        this.publications = project.container(Publication)
    }

    void publications(Closure closure) {
        this.publications.configure(closure)
        onPublicationListener.onPublicationCreated(publications)
    }

    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

}