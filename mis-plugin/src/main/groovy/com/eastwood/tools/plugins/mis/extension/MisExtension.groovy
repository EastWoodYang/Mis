package com.eastwood.tools.plugins.mis.extension

interface MisExtension {

    void repository(Closure closure)

    void sourceSets(Closure closure)

}