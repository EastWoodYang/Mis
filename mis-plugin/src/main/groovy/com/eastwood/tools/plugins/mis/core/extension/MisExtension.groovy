package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

class MisExtension {

    Project project
    Action<? super RepositoryHandler> configure
    OnPublicationListener onPublicationListener

    MisExtension(Project project, OnPublicationListener listener) {
        this.project = project
        this.onPublicationListener = listener
    }

    void publications(Closure closure) {
        NamedDomainObjectContainer<Publication> publications = project.container(Publication)
        publications.configure(closure)
        publications.each {
            onPublicationListener.onPublicationCreated(it)
        }
    }

    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

}