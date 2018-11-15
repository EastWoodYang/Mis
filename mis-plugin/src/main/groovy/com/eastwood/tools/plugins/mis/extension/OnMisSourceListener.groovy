package com.eastwood.tools.plugins.mis.extension

import org.gradle.api.NamedDomainObjectContainer

interface OnMisSourceListener {

    void onMisSourceSetsCreated(NamedDomainObjectContainer<MisSource> sourceSets)

}
