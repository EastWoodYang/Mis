package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class MisPlugin implements Plugin<Project> {

    Project project
    List<Map<String, ?>> misProviderList
    boolean initMisDir

    void apply(Project project) {
        this.project = project
        if (!MisUtil.isAndroidPlugin(project)) {
            throw new RuntimeException("The android or android-library plugin must be applied to the project.")
        }

        project.dependencies.metaClass.misProvider { String value ->
            String[] values = value.split(":")
            if (values.length >= 3) {
                return handleMisProvider(values[0], values[1], values[2])
            } else if (values.length == 2) {
                return handleMisProvider(values[0], values[1], null)
            } else {
                throw new IllegalArgumentException("'${value}' is illege argument of misProvider(), the following types/formats are supported:" +
                        "\n  - String or CharSequence values, for example 'org.gradle:gradle-core:1.0'." +
                        "\n  - Maps, for example [group: 'org.gradle', name: 'gradle-core', version: '1.0'].")
            }
        }

        project.dependencies.metaClass.misProvider { Map<String, ?> options ->
            return handleMisProvider(options.group, options.name, options.version)
        }

        misProviderList = new ArrayList<>()
        project.dependencies.metaClass.misSource { Map<String, ?> options ->
            if (!initMisDir) {
                setMisDir(project)
                initMisDir = true
            }

            misProviderList << options
            if (options.containsKey("version")) {
                return handleMavenJar(project, options)
            } else {
                return handleLocalJar(project, options)
            }
        }

        project.afterEvaluate {
            if (!initMisDir) {
                setMisDir(project)
            }
            misProviderList.each {
                def taskName
                String microModuleName = it.get("microModuleName")
                if (microModuleName != null) {
                    taskName = 'uploadMis_' + microModuleName
                } else {
                    taskName = 'uploadMis'
                }
                UploadTask uploadSDKTask = project.getTasks().create(taskName, UploadTask.class)
                uploadSDKTask.setGroup("upload")
                uploadSDKTask.options = it
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

    Object handleMisProvider(String group, String name, String version) {
        if (version == null) {
            String fileName = name + ".jar"
            File target = project.rootProject.file(".gradle/mis/" + group + "/" + fileName)
            return project.files(target)
        } else {
            String fileName = name + ".jar"
            File target = project.rootProject.file(".gradle/mis/" + group + "/" + fileName)
            if (target.exists()) {
                return project.files(target)
            } else {
                return "${group}:${name}:${version}"
            }
        }
    }

    Object handleLocalJar(Project project, Map<String, ?> options) {
        File releaseJar = JarPacker.packReleaseJar(project, options)
        if (releaseJar == null) return []
        File targetParent = project.rootProject.file(".gradle/mis/" + options.group)
        targetParent.mkdirs()
        File target = new File(targetParent, options.name + ".jar")
        MisUtil.copyFile(releaseJar, target)
        return project.files(target)
    }

    Object handleMavenJar(Project project, Map<String, ?> options) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + options.group)
        File target = new File(targetGroup, options.name + ".jar")
        if (target.exists()) {
            return project.files(target)
        } else {
            boolean result = JarPacker.hasModifiedSource(project, options)
            if (!result) {
                Map<String, ?> optionsCopy = options.clone()
                optionsCopy.remove("dependencies")
                optionsCopy.remove("microModuleName")
                return optionsCopy
            }

            String filePath = null
            String fileName = options.name + "-" + options.version + ".jar"

            Map<String, ?> optionsCopy = options.clone()
            optionsCopy.remove("dependencies")
            optionsCopy.remove("microModuleName")

            def random = new Random()
            def name = "mis_" + random.nextLong()
            project.configurations.create(name)
            project.dependencies.add(name, optionsCopy)
            project.configurations.getByName(name).resolve().each {
                if (it.name.endsWith(fileName)) {
                    filePath = it.absolutePath
                }
            }

            def releaseJar = JarPacker.packReleaseJar(project, options)
            if (releaseJar == null) {
                return optionsCopy
            }
            boolean equals = MisUtil.compareJar(releaseJar.absolutePath, filePath)
            if (equals) {
                releaseJar.delete()
                return optionsCopy
            } else {
                targetGroup = project.rootProject.file(".gradle/mis/" + options.group)
                targetGroup.mkdirs()
                target = new File(targetGroup, options.name + ".jar")
                MisUtil.copyFile(releaseJar, target)
                return project.files(target)
            }
        }

    }

}