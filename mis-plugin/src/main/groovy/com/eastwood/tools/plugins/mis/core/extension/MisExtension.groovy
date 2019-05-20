package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

class MisExtension {

    Project project
    OnPublicationListener listener
    Map<String, Publication> publicationMap

    Action<? super RepositoryHandler> configure

    MisExtension(Project project, OnPublicationListener listener) {
        this.project = project
        this.listener = listener
        this.publicationMap = new HashMap<>()
    }

    void publications(Closure closure) {
        NamedDomainObjectContainer<Publication> publications = project.container(Publication)
        publications.configure(closure)
        publications.each {
            if (publicationMap.containsKey(it.name)) {
                return
            }

            publicationMap.put(it.name, it)
            listener.onPublicationAdded(it)
        }
    }

    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

}
