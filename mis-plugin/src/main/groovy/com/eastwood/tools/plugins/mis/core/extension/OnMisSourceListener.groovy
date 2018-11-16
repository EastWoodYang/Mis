package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.NamedDomainObjectContainer

interface OnMisSourceListener {

    void onMisSourceSetsCreated(NamedDomainObjectContainer<MisSource> sourceSets)

}
