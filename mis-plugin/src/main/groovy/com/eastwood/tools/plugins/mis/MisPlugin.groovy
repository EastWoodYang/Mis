package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import com.eastwood.tools.plugins.mis.extension.DefaultMisExtension
import com.eastwood.tools.plugins.mis.extension.MisExtension
import com.eastwood.tools.plugins.mis.extension.MisSource
import com.eastwood.tools.plugins.mis.extension.OnMisSourceListener
import com.intellij.util.text.VersionComparatorUtil
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class MisPlugin implements Plugin<Project> {

    Project project
    boolean initMisSrcDir

    MisMavenExtension mavenExtension

    List<MisSource> misSourceList
    List<MisSource> uploadMavenOptionList

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
        def isMicroModule = Util.isMicroModule(project)

        OnMisSourceListener onMisSourceListener = new OnMisSourceListener() {
            @Override
            void onMisSourceSetsCreated(NamedDomainObjectContainer<MisSource> sourceSets) {
                BaseExtension android = project.extensions.getByName('android')
                sourceSets.each {
                    if (!initMisSrcDir) {
                        MisUtil.setProjectMisSourceDirs(project)
                        initMisSrcDir = true
                    }

                    misSourceList << it
                    MisSource misSource = it
                    if (isMicroModule) {
                        def result = misSource.name.split(":")
                        if (result.length >= 1) {
                            misSource.microModuleName = result[0]
                        } else {
                            // TODO
                        }
                        misSource.flavorName = result.length >= 1 ? "main" : result[1]
                    } else {
                        misSource.flavorName = misSource.name
                    }

                    List<String> paths = new ArrayList<>()
                    def flavorSourceSets = android.sourceSets.getByName(misSource.flavorName)
                    flavorSourceSets.aidl.srcDirs.each {
                        if (!it.absolutePath.endsWith("mis")) return

                        if (misSource.microModuleName != null) {
                            if (it.absolutePath.endsWith(misSource.microModuleName + "${File.separator}src${File.separator + misSource.flavorName + File.separator}mis")) {
                                paths.add(it)
                            }
                        } else {
                            paths.add(it)
                        }
                    }
                    misSource.paths = paths

                    if (misSource.version != null && !misSource.version.isEmpty()) {
                        return handleMavenJar(project, misSource)
                    } else {
                        return handleLocalJar(project, misSource)
                    }
                }
            }
        }
        project.extensions.create(MisExtension, 'mis', DefaultMisExtension, project, onMisSourceListener)

        project.dependencies.metaClass.misProvider { String value ->
            println value
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
                MisSource misSource = it
                def publicationName = 'Mis[' + misSource.artifactId + "]"
                configPublication(misSource, publicationName)
                String publishMavenTaskName = "publish" + publicationName + "PublicationToMavenRepository"
                String publishMavenSnapshotTaskName = "publish" + publicationName + "PublicationToMavenSnapshotRepository"
                project.tasks.whenTaskAdded {
                    if (it.name == publishMavenTaskName || it.name == publishMavenSnapshotTaskName) {
                        def taskName = 'compileMis[' + misSource.artifactId + ']Source'
                        def compileTask = project.getTasks().findByName(taskName)
                        if (compileTask == null) {
                            compileTask = project.getTasks().create(taskName, CompileSourceTask.class)
                            compileTask.misSource = misSource
                            it.dependsOn compileTask
                            it.doLast {
                                File groupDir = project.rootProject.file(".gradle/mis/" + misSource.groupId)
                                new File(groupDir, misSource.artifactId + ".jar").delete()
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
            def result, resultVersion
            MisSource misSource = MisUtil.getMisSourceFormManifest(project, groupId, artifactId)
            if (misSource == null || misSource.version == "") {
                if (version == null) {
                    result = []
                } else {
                    result = "${groupId}:${artifactId}:${version}"
                    resultVersion = version
                }
            } else {
                result = "${groupId}:${artifactId}:${misSource.version}"
                resultVersion = misSource.version
            }

            project.gradle.buildFinished {
                misSource = MisUtil.getMisSourceFormManifest(project, groupId, artifactId)
                if (misSource == null) {
                    throw new RuntimeException("Could not find " + groupId + ":" + artifactId + ".")
                } else if (result == []) {
                    if (!misSource.invalid && misSource.version == "") {
                        throw new RuntimeException("Please Sync Project with Gradle files again.")
                    }
                } else if (VersionComparatorUtil.compare(misSource.version, resultVersion) > 0) {
                    throw new RuntimeException("Please Sync Project with Gradle files again.")
                }
            }
            return result
        }
    }

    Object handleLocalJar(Project project, MisSource misSource) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + misSource.groupId)
        targetGroup.mkdirs()
        File target = new File(targetGroup, misSource.artifactId + ".jar")
        if (target.exists()) {
            boolean hasModifiedSource = SourceStateUtil.hasModifiedSourceFile(project, misSource)
            if (!hasModifiedSource) {
                return project.files(target)
            }
        }
        File releaseJar = JarUtil.packJavaSourceJar(project, misSource)
        if (releaseJar == null) {
            target.delete()
            misSource.invalid = true
            return []
        }

        SourceStateUtil.updateSourceFileState(project, misSource)
        Util.copyFile(releaseJar, target)
        return project.files(target)
    }

    Object handleMavenJar(Project project, MisSource misSource) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + misSource.groupId)
        File target = new File(targetGroup, misSource.artifactId + ".jar")
        if (target.exists()) {
            if (!SourceStateUtil.hasModifiedSourceFile(project, misSource)) {
                uploadMavenOptionList << misSource
                return project.files(target)
            }
        } else {
            if (!SourceStateUtil.hasModifiedSourceFile(project, misSource)) {
                return Util.optionsFilter(misSource)
            }
        }

        def releaseJar = JarUtil.packJavaSourceJar(project, misSource)
        if (releaseJar == null) {
            target.delete()
            misSource.invalid = true
            return []
        }

        boolean equals = JarUtil.compareMavenJar(project, misSource, releaseJar.absolutePath)
        if (equals) {
            target.delete()
            SourceStateUtil.updateSourceFileState(project, misSource)
            return Util.optionsFilter(misSource)
        } else {
            uploadMavenOptionList << misSource
            SourceStateUtil.updateSourceFileState(project, misSource)
            targetGroup = project.rootProject.file(".gradle/mis/" + misSource.groupId)
            targetGroup.mkdirs()
            target = new File(targetGroup, misSource.artifactId + ".jar")
            Util.copyFile(releaseJar, target)
            return project.files(target)
        }
    }

    def configPublication(MisSource misSource, publicationName) {

        def publishing = project.extensions.getByName('publishing')
        MavenPublication mavenPublication = publishing.publications.maybeCreate(publicationName, MavenPublication)
        mavenPublication.groupId = misSource.groupId
        mavenPublication.artifactId = misSource.artifactId
        mavenPublication.version = misSource.version
        mavenPublication.pom.packaging = 'jar'

        def typeDir = Util.getTypeDir(project, misSource)
        def outputsDir = new File(typeDir, "outputs")
        mavenPublication.artifact source: new File(outputsDir, "classes.jar")
        mavenPublication.artifact source: new File(outputsDir, "classes-source.jar"), classifier: 'sources'

        if (misSource.dependencies != null && misSource.dependencies.size() > 0) {
            def dependencies = misSource.dependencies
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