package com.eastwood.tools.plugins.mis

import org.gradle.api.Plugin
import org.gradle.api.Project

class MisMavenPlugin implements Plugin<Project> {

    void apply(Project project) {

        project.extensions.add('misMaven', MisMavenExtension)

    }

}