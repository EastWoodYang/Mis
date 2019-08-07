package com.eastwood.tools.plugins.mis

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.eastwood.tools.plugins.mis.extension.MisComponent
import com.eastwood.tools.plugins.mis.extension.MisComponentExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil

class MisComponentPlugin implements Plugin<Project> {

    static Map<String, MisComponent> misComponents

    Project project

    void apply(Project project) {
        this.project = project

        if (project == project.rootProject) {
            project.metaClass.misComponent { Closure closure ->
                MisComponentExtension misComponentExtension = new MisComponentExtension()
                ConfigureUtil.configure(closure, misComponentExtension)
                misComponents = misComponentExtension.getMisComponents()
            }

            project.allprojects.each {
                if (it == project) return
                Project childProject = it

                childProject.plugins.whenObjectAdded {
                    if (it instanceof AppPlugin || it instanceof LibraryPlugin) {
                        childProject.pluginManager.apply('mis-component')
                    }
                }
            }

        }

        project.dependencies.metaClass.misComponent { String value ->
            if (misComponents == null || !misComponents.containsKey(value)) return []

            MisComponent misComponent = misComponents.get(value)
            if (project.gradle.startParameter.taskNames.isEmpty()) {
                def result = project.dependencies.metaClass.respondsTo(project.dependencies, 'misPublication')
                if (result.isEmpty()) {
                    return misComponent.sdk
                } else {
                    return project.dependencies.misPublication(misComponent.sdk)
                }
            } else {
                if (misComponent.impl.startsWith(":")) {
                    return project.project(misComponent.impl)
                } else {
                    return misComponent.impl
                }
            }
        }
    }

}