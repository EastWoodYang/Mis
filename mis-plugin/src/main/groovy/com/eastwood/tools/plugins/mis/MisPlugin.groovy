package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project

class MisPlugin implements Plugin<Project> {

    void apply(Project project) {

        if (!isAndroidPlugin(project)) {
            throw new RuntimeException("The android or android-library plugin must be applied to the project.")
        }

        NamedDomainObjectContainer<MisUploadConfig> uploadConfigContainer = project.container(MisUploadConfig)
        project.extensions.add('uploadMis', uploadConfigContainer)

        project.dependencies.metaClass.misProvider { path ->

            return []
        }

        project.afterEvaluate {

            setMisDir(project)

            project.tasks.preBuild.doFirst {
                setMisDir(project)
            }

            def misConfig = project.extensions.getByName('uploadMis')
            if (isMicroModule(project)) {
                misConfig.each {
                    def taskName
                    if (it.ignoreMicroModule) {
                        taskName = 'uploadMis'
                        it.name = 'all'
                    } else {
                        taskName = 'uploadMis_' + it.name
                    }
                    UploadSDKTask uploadSDKTask = project.getTasks().create(taskName, UploadSDKTask.class)
                    uploadSDKTask.setGroup("upload")
                    uploadSDKTask.uploadConfig = it
                }
            } else {
                misConfig.each {
                    if (it.name == 'main') {
                        def taskName = 'uploadMis'
                        UploadSDKTask uploadSDKTask = project.getTasks().create(taskName, UploadSDKTask.class)
                        uploadSDKTask.setGroup("upload")
                        uploadSDKTask.uploadConfig = it
                    }
                }
            }
        }
    }

    def setMisDir(Project project) {
        def type = "main"
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.getByName(type)
        obj.java.srcDirs.each {
            obj.aidl.srcDirs(it.absolutePath.replace('java', 'mis'))
        }
    }

    def isMicroModule(Project project) {
        return project.plugins.findPlugin("micro-module")
    }

    def isAndroidPlugin(Project project) {
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            return true
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            return true
        } else {
            return false
        }
    }

}