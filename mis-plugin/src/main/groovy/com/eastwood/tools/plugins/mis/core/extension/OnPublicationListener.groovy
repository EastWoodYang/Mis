package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.NamedDomainObjectContainer

interface OnPublicationListener {

    void onPublicationCreated(NamedDomainObjectContainer<Publication> publications)

}
