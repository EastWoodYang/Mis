package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.util.ConfigureUtil

class MisExtension {

    int compileSdkVersion
    CompileOptions compileOptions
    Action<? super RepositoryHandler> configure

    Project childProject
    OnMisExtensionListener listener
    Map<String, Publication> publicationMap

    MisExtension(OnMisExtensionListener listener) {
        this.listener = listener
        this.publicationMap = new HashMap<>()
    }

    void compileSdkVersion(int version) {
        compileSdkVersion = version
    }

    void publications(Closure closure) {
        NamedDomainObjectContainer<Publication> publications = childProject.container(Publication)
        ConfigureUtil.configure(closure, publications)
        publications.each {
            listener.onPublicationAdded(childProject, it)
        }
    }

    void compileOptions(Closure closure) {
        compileOptions = new CompileOptions()
        ConfigureUtil.configure(closure, compileOptions)
    }

    void repositories(Action<? super RepositoryHandler> configure) {
        this.configure = configure
    }

}
