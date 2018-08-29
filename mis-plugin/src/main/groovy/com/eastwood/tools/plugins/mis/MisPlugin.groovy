package com.eastwood.tools.plugins.mis

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class MisPlugin implements Plugin<Project> {

    Project project
    boolean initMisSrcDir

    MisMavenExtension mavenExtension

    List<Map<String, ?>> misSourceList
    List<Map<String, ?>> uploadMavenOptionList

    void apply(Project project) {

        if (!Util.isAndroidPlugin(project)) {
            throw new RuntimeException("The android or android-library plugin must be applied to the project.")
        }

        this.project = project
        misSourceList = new ArrayList<>()
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
                        "\n  - Maps, for example [groupId: 'org.gradle', artifactId: 'gradle-core', version: '1.0'].")
            }
        }

        project.dependencies.metaClass.misProvider { Map<String, ?> options ->
            return handleMisProvider(options.groupId, options.artifactId, options.version)
        }

        project.dependencies.metaClass.misSource { Map<String, ?> options ->
            if (!initMisSrcDir) {
                MisUtil.setProjectMisSourceDirs(project)
                initMisSrcDir = true
            }
            misSourceList << options

            if (options.containsKey("version")) {
                return handleMavenJar(project, options)
            } else {
                return handleLocalJar(project, options)
            }
        }

        project.afterEvaluate {
            if (!initMisSrcDir) {
                MisUtil.setProjectMisSourceDirs(project)
            }

            MisUtil.updateMisSourceManifest(project, misSourceList)

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
                def publicationName = 'Mis[' + options.artifactId + "]"
                configPublication(options, publicationName)
                String publishMavenTaskName = "publish" + publicationName + "PublicationToMavenRepository"
                String publishMavenSnapshotTaskName = "publish" + publicationName + "PublicationToMavenSnapshotRepository"
                project.tasks.whenTaskAdded {
                    if (it.name == publishMavenTaskName || it.name == publishMavenSnapshotTaskName) {
                        def taskName = 'compileMis[' + options.artifactId + ']Source'
                        def compileTask = project.getTasks().findByName(taskName)
                        if (compileTask == null) {
                            compileTask = project.getTasks().create(taskName, CompileSourceTask.class)
                            compileTask.options = options
                            it.dependsOn compileTask
                            it.doLast {
                                File groupDir = project.rootProject.file(".gradle/mis/" + options.groupId)
                                new File(groupDir, options.artifactId + ".jar").delete()
                            }
                        }
                    }
                }
            }
        }
    }

    Object handleMisProvider(String groupId, String artifactId, String version) {
        String fileName = artifactId + ".jar"
        File target = project.rootProject.file(".gradle/mis/" + groupId + "/" + fileName)
        if (target.exists()) {
            return project.files(target)
        } else {
            def misSourceVersion = MisUtil.getMisSourceVersionFormManifest(project, groupId, artifactId)
            if(misSourceVersion == null || misSourceVersion == "") {
                if(version == null) {
                    throw new RuntimeException("Sync project with Gradle Files again.")
                } else {
                    return "${groupId}:${artifactId}:${version}"
                }
            } else {
                return "${groupId}:${artifactId}:${misSourceVersion}"
            }
        }
    }

    Object handleLocalJar(Project project, Map<String, ?> options) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + options.groupId)
        targetGroup.mkdirs()
        File target = new File(targetGroup, options.artifactId + ".jar")
        if (target.exists()) {
            boolean hasModifiedSource = SourceStateUtil.hasModifiedSourceFile(project, options)
            if (!hasModifiedSource) {
                return project.files(target)
            }
        }
        File releaseJar = JarUtil.packJavaSourceJar(project, options)
        if (releaseJar == null) {
            target.delete()
            return []
        }

        SourceStateUtil.updateSourceFileState(project, options)
        Util.copyFile(releaseJar, target)
        return project.files(target)
    }

    Object handleMavenJar(Project project, Map<String, ?> options) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + options.groupId)
        File target = new File(targetGroup, options.artifactId + ".jar")
        if (target.exists()) {
            if (!SourceStateUtil.hasModifiedSourceFile(project, options)) {
                uploadMavenOptionList << options
                return project.files(target)
            }
        } else {
            if (!SourceStateUtil.hasModifiedSourceFile(project, options)) {
                return Util.optionsFilter(options)
            }
        }

        def releaseJar = JarUtil.packJavaSourceJar(project, options)
        if (releaseJar == null) {
            target.delete()
            return []
        }

        boolean equals = JarUtil.compareMavenJar(project, options, releaseJar.absolutePath)
        if (equals) {
            target.delete()
            SourceStateUtil.updateSourceFileState(project, options)
            return Util.optionsFilter(options)
        } else {
            uploadMavenOptionList << options
            SourceStateUtil.updateSourceFileState(project, options)
            targetGroup = project.rootProject.file(".gradle/mis/" + options.groupId)
            targetGroup.mkdirs()
            target = new File(targetGroup, options.artifactId + ".jar")
            Util.copyFile(releaseJar, target)
            return project.files(target)
        }
    }

    def configPublication(Map<String, ?> options, publicationName) {

        def publishing = project.extensions.getByName('publishing')
        MavenPublication mavenPublication = publishing.publications.maybeCreate(publicationName, MavenPublication)
        mavenPublication.groupId = options.groupId
        mavenPublication.artifactId = options.artifactId
        mavenPublication.version = options.version
        mavenPublication.pom.packaging = 'jar'

        def typeDir = Util.getTypeDir(project, options)
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