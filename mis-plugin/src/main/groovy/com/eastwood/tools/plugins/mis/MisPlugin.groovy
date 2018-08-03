package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import org.apache.commons.lang.StringUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class MisPlugin implements Plugin<Project> {

    Project project
    List<Map<String, ?>> uploadMavenOptionList
    boolean initMisSrcDir

    MisMavenExtension mavenExtension

    void apply(Project project) {
        this.project = project
        if (!MisUtil.isAndroidPlugin(project)) {
            throw new RuntimeException("The android or android-library plugin must be applied to the project.")
        }
        uploadMavenOptionList = new ArrayList<>()

        mavenExtension = project.extensions.findByName("misMaven")
        if (mavenExtension == null) {
            mavenExtension = project.rootProject.extensions.findByName("misMaven")
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

        project.dependencies.metaClass.misSource { Map<String, ?> options ->
            if (!initMisSrcDir) {
                setMisSrcDir(project)
                initMisSrcDir = true
            }

            if (options.containsKey("version")) {
                return handleMavenJar(project, options)
            } else {
                return handleLocalJar(project, options)
            }
        }

        project.afterEvaluate {
            if (!initMisSrcDir) {
                setMisSrcDir(project)
            }

            if (mavenExtension == null || uploadMavenOptionList.size() == 0) {
                return
            }

            project.plugins.apply('maven-publish')
            def publishing = project.extensions.getByName('publishing')
            publishing.repositories {
                if (mavenExtension.repository != null) {
                    maven {
                        name = 'Maven'
                        url = mavenExtension.repository
                        if (mavenExtension.username != null || mavenExtension.password != null) {
                            credentials {
                                username = mavenExtension.username
                                password = mavenExtension.password
                            }
                        }
                    }
                }

                if (mavenExtension.snapshotRepository != null) {
                    maven {
                        name = 'MavenSnapshot'
                        url = mavenExtension.snapshotRepository
                        if (mavenExtension.username != null || mavenExtension.password != null) {
                            credentials {
                                username = mavenExtension.username
                                password = mavenExtension.password
                            }
                        }
                    }
                }
            }

            uploadMavenOptionList.each {
                def options = it
                def publicationName = 'mis[' + options.name + "]"
                configPublication(options, publicationName)
                String publishMavenTaskName = "publish" + StringUtils.capitalize(publicationName) + "PublicationToMavenRepository"
                String publishMavenSnapshotTaskName = "publish" + StringUtils.capitalize(publicationName) + "PublicationToMavenSnapshotRepository"
                project.tasks.whenTaskAdded {
                    if (it.name == publishMavenTaskName || it.name == publishMavenSnapshotTaskName) {
                        def taskName = 'compileMis[' + options.name + ']Source'
                        def compileTask = project.getTasks().findByName(taskName)
                        if (compileTask == null) {
                            compileTask = project.getTasks().create(taskName, CompileMisSourceTask.class)
                            compileTask.options = options
                            it.dependsOn compileTask
                            it.doLast {
                                File group = project.rootProject.file(".gradle/mis/" + options.group)
                                new File(group, options.name + ".jar").delete()
                            }
                        }
                    }
                }
            }
        }
    }

    def setMisSrcDir(Project project) {
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
        File targetGroup = project.rootProject.file(".gradle/mis/" + options.group)
        targetGroup.mkdirs()
        File target = new File(targetGroup, options.name + ".jar")
        if (target.exists()) {
            boolean hasModifiedSource = JarPacker.hasModifiedSource(project, options)
            if (!hasModifiedSource) {
                return project.files(target)
            }
        }
        File releaseJar = JarPacker.packReleaseJar(project, options)
        if (releaseJar == null) {
            target.delete()
            return []
        }

        MisUtil.copyFile(releaseJar, target)
        return project.files(target)
    }

    Object handleMavenJar(Project project, Map<String, ?> options) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + options.group)
        File target = new File(targetGroup, options.name + ".jar")
        if (target.exists()) {
            if (!JarPacker.hasModifiedSource(project, options)) {
                uploadMavenOptionList << options
                return project.files(target)
            }
        } else {
            if (!JarPacker.hasModifiedSource(project, options)) {
                return MisUtil.optionsFilter(options)
            }
        }

        def releaseJar = JarPacker.packReleaseJar(project, options)
        if (releaseJar == null) {
            target.delete()
            return []
        }

        boolean equals = MisUtil.compareMavenJar(project, options, releaseJar.absolutePath)
        if (equals) {
            target.delete()
            return MisUtil.optionsFilter(options)
        } else {
            uploadMavenOptionList << options
            targetGroup = project.rootProject.file(".gradle/mis/" + options.group)
            targetGroup.mkdirs()
            target = new File(targetGroup, options.name + ".jar")
            MisUtil.copyFile(releaseJar, target)
            return project.files(target)
        }
    }

    def configPublication(Map<String, ?> options, publicationName) {

        def publishing = project.extensions.getByName('publishing')
        MavenPublication mavenPublication = publishing.publications.maybeCreate(publicationName, MavenPublication)
        mavenPublication.groupId = options.group
        mavenPublication.artifactId = options.name
        mavenPublication.version = options.version
        mavenPublication.pom.packaging = 'jar'

        def typeDir = JarPacker.getTypeDir(project, options)
        def outputsDir = new File(typeDir, "outputs")
        mavenPublication.artifact source: new File(outputsDir, "classes.jar")
        mavenPublication.artifact source: new File(outputsDir, "classes-source.jar"), classifier: 'sources'

        if (options.dependencies != null && options.dependencies.size() > 0) {
            def dependencies = options.dependencies
            mavenPublication.pom.withXml {
                def dependenciesNode = asNode().appendNode('dependencies')
                dependencies.each {
                    def gav = it.split(":")
                    def dependencyNode = dependenciesNode.appendNode('dependency')
                    dependencyNode.appendNode('groupId', gav[0])
                    dependencyNode.appendNode('artifactId', gav[1])
                    dependencyNode.appendNode('version', gav[2])
                }
            }
        }

    }

}