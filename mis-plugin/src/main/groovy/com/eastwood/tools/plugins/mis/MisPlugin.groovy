package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import com.eastwood.tools.plugins.mis.core.JarUtil
import com.eastwood.tools.plugins.mis.core.MisUtil
import com.eastwood.tools.plugins.mis.core.extension.MavenRepository
import com.eastwood.tools.plugins.mis.core.extension.MisExtension
import com.eastwood.tools.plugins.mis.core.extension.MisSource
import com.eastwood.tools.plugins.mis.core.extension.OnMisSourceListener
import com.eastwood.tools.plugins.mis.core.state.StateUtil
import org.gradle.api.GradleException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class MisPlugin implements Plugin<Project> {

    Project project
    boolean isMicroModule
    boolean initMisSrcDir

    List<MisSource> misSourceList
    Map<String, MisSource> misSourcePublishMap

    void apply(Project project) {

        if (!MisUtil.hasAndroidPlugin(project)) {
            throw new GradleException("The android or android-library plugin must be applied to the project.")
        }

        this.project = project
        misSourceList = new ArrayList<>()
        misSourcePublishMap = new HashMap<>()
        isMicroModule = MisUtil.isMicroModule(project)

        OnMisSourceListener onMisSourceListener = new OnMisSourceListener() {
            @Override
            void onMisSourceSetsCreated(NamedDomainObjectContainer<MisSource> sourceSets) {
                MisUtil.setProjectMisSourceDirs(project)
                initMisSrcDir = true

                BaseExtension android = project.extensions.getByName('android')
                sourceSets.each {
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
                                paths.add(it.absolutePath)
                            }
                        } else {
                            paths.add(it.absolutePath)
                        }
                    }
                    misSource.paths = paths

                    if (misSource.version != null && !misSource.version.isEmpty()) {
                        handleMavenJar(project, misSource)
                    } else {
                        handleLocalJar(project, misSource)
                    }
                }
            }
        }
        MisExtension misExtension = project.extensions.create('mis', MisExtension, project, onMisSourceListener)

        project.dependencies.metaClass.misProvider { Object value ->
            def groupId, artifactId, version
            if (value instanceof String) {
                String[] values = value.split(":")
                if (values.length >= 3) {
                    groupId = values[0]
                    artifactId = values[1]
                    version = values[2]
                } else if (values.length == 2) {
                    groupId = values[0]
                    artifactId = values[1]
                    version = null
                }
            } else if (value instanceof Map<String, ?>) {
                groupId = value.groupId
                artifactId = value.artifactId
                version = value.version
            }

            if (groupId != null && artifactId != null) {
                return handleMisProvider(groupId, artifactId, version)
            } else {
                throw new IllegalArgumentException("'${value}' is illege argument of misProvider(), the following types/formats are supported:" +
                        "\n  - String or CharSequence values, for example 'org.gradle:gradle-core:1.0'." +
                        "\n  - Maps, for example [groupId: 'org.gradle', artifactId: 'gradle-core', version: '1.0'].")
            }
        }

        project.afterEvaluate {
            if (!initMisSrcDir) {
                MisUtil.setProjectMisSourceDirs(project)
            }

            MisUtil.updateMisSourceManifest(project, misSourceList)

            if (misSourcePublishMap.size() == 0 || misExtension.repository == null) {
                return
            }

            project.plugins.apply('maven-publish')
            def publishing = project.extensions.getByName('publishing')
            publishing.repositories {
                maven {
                    MavenRepository repository = misExtension.repository
                    url = repository.url
                    if (repository.credentials != null) {
                        credentials repository.credentials
                    }
                }
            }

            misSourcePublishMap.each {
                createPublishPublicationTask(it.value)
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
                    resultVersion = null
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
                    throw new GradleException("Could not find " + groupId + ":" + artifactId + ".")
                } else if (result == []) {
                    if (!misSource.invalid && misSource.version == "") {
                        throw new GradleException("Please Sync Project with Gradle files again.")
                    }
                } else if (misSource.version != resultVersion) {
                    throw new GradleException("Please Sync Project with Gradle files again.")
                }
            }
            return result
        }
    }

    def handleLocalJar(Project project, MisSource misSource) {
        File targetGroup = project.rootProject.file(".gradle/mis/" + misSource.groupId)
        if (!targetGroup.exists()) {
            targetGroup.mkdirs()
        }

        File target = new File(targetGroup, misSource.artifactId + ".jar")
        if (target.exists()) {
            boolean hasModifiedSource = StateUtil.hasModifiedSourceFile(project, misSource)
            if (!hasModifiedSource) {
                return
            }
        }

        File releaseJar = JarUtil.packJavaSourceJar(project, misSource)
        if (releaseJar == null) {
            misSource.invalid = true
            if (target.exists()) {
                target.delete()
            }
            return
        }

        StateUtil.updateSourceFileState(project, misSource)
        MisUtil.copyFile(releaseJar, target)
    }

    Object handleMavenJar(Project project, MisSource misSource) {
        boolean hasModifiedSource = StateUtil.hasModifiedSourceFile(project, misSource)
        File targetGroup = project.rootProject.file(".gradle/mis/" + misSource.groupId)
        File target = new File(targetGroup, misSource.artifactId + ".jar")
        if (target.exists()) {
            if (!hasModifiedSource) {
                misSourcePublishMap.put(misSource.artifactId, misSource)
                return
            }
        } else if (!hasModifiedSource) {
            return
        }

        def releaseJar = JarUtil.packJavaSourceJar(project, misSource)
        if (releaseJar == null) {
            misSource.invalid = true
            if (target.exists()) {
                target.delete()
            }
            return
        }

        boolean equals = JarUtil.compareMavenJar(project, misSource, releaseJar.absolutePath)
        if (equals) {
            target.delete()
            StateUtil.updateSourceFileState(project, misSource)
        } else {
            misSourcePublishMap.put(misSource.artifactId, misSource)
            StateUtil.updateSourceFileState(project, misSource)
            targetGroup = project.rootProject.file(".gradle/mis/" + misSource.groupId)
            targetGroup.mkdirs()
            target = new File(targetGroup, misSource.artifactId + ".jar")
            MisUtil.copyFile(releaseJar, target)
        }
    }

    void createPublishPublicationTask(MisSource misSource) {
        def publicationName = 'Mis[' + misSource.artifactId + "]"
        configMisSourcePublication(misSource, publicationName)
        String publishMavenTaskName = "publish" + publicationName + "PublicationToMavenRepository"
        project.tasks.whenTaskAdded {
            if (it.name == publishMavenTaskName) {
                def taskName = 'compileMis[' + misSource.artifactId + ']Source'
                def compileTask = project.getTasks().findByName(taskName)
                if (compileTask == null) {
                    compileTask = project.getTasks().create(taskName, CompileMisTask.class)
                    compileTask.misSource = misSource
                    compileTask.dependsOn 'clean'
                    it.dependsOn compileTask
                    it.doLast {
                        File groupDir = project.rootProject.file(".gradle/mis/" + misSource.groupId)
                        new File(groupDir, misSource.artifactId + ".jar").delete()
                    }
                }
            }
        }
    }

    void configMisSourcePublication(MisSource misSource, String publicationName) {
        def publishing = project.extensions.getByName('publishing')
        MavenPublication mavenPublication = publishing.publications.maybeCreate(publicationName, MavenPublication)
        mavenPublication.groupId = misSource.groupId
        mavenPublication.artifactId = misSource.artifactId
        mavenPublication.version = misSource.version
        mavenPublication.pom.packaging = 'jar'

        def typeDir = MisUtil.getTypeDir(project, misSource)
        def outputsDir = new File(typeDir, "outputs")
        mavenPublication.artifact source: new File(outputsDir, "classes.jar")
        mavenPublication.artifact source: new File(outputsDir, "classes-source.jar"), classifier: 'sources'

        if (misSource.dependencies != null) {
            mavenPublication.pom.withXml {
                def dependenciesNode = asNode().appendNode('dependencies')
                if (misSource.dependencies.implementation != null) {
                    misSource.dependencies.implementation.each {
                        def gav = it.split(":")
                        def dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', gav[0])
                        dependencyNode.appendNode('artifactId', gav[1])
                        dependencyNode.appendNode('version', gav[2])
                        dependencyNode.appendNode('scope', 'implementation')
                    }
                }
            }
        }

    }

}