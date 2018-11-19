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
        int index = this.publications.size()
        this.publications.configure(closure)
        List<Publication> publicationList = this.publications.toList()
        for (int i = index; i < publicationList.size(); i++) {
            onPublicationListener.onPublicationCreated(publicationList.get(i))
        }
    }

    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

}