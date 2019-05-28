package com.eastwood.tools.plugins.mis.core.extension

import org.gradle.api.JavaVersion

class CompileOptions {

    JavaVersion sourceCompatibility
    JavaVersion targetCompatibility

    void sourceCompatibility(Object value) {
        sourceCompatibility = value
    }

    void targetCompatibility(Object value) {
        targetCompatibility = value
    }

}