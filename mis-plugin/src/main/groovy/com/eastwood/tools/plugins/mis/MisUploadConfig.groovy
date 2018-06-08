package com.eastwood.tools.plugins.mis

class MisUploadConfig {

    String name

    String groupId
    String artifactId
    String version

    String[] dependencies

    Closure[] sourceFilters
    boolean ignoreMicroModule

    MisUploadConfig(String name) {
        this.name = name
    }

}