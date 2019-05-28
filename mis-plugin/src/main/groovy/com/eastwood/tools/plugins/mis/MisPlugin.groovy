package com.eastwood.tools.plugins.mis

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.eastwood.tools.plugins.mis.core.*
import com.eastwood.tools.plugins.mis.core.extension.Dependencies
import com.eastwood.tools.plugins.mis.core.extension.MisExtension
import com.eastwood.tools.plugins.mis.core.extension.OnMisExtensionListener
import com.eastwood.tools.plugins.mis.core.extension.Publication
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPublication

class MisPlugin implements Plugin<Project> {

    static MisExtension misExtension
    static String androidJarPath

    static PublicationManager publicationManager

    Project project
    File misDir
    boolean executePublish
    String publishPublication

    Map<String, Publication> publicationPublishMap

    void apply(Project project) {
        this.project = project
        misDir = new File(project.rootProject.projectDir, '.gradle/mis')
        if (!misDir.exists()) {
            misDir.mkdirs()
        }

        if (project == project.rootProject) {

            project.gradle.getStartParameter().taskNames.each {
                if (it == 'clean') {
                    if (!misDir.deleteDir()) {
                        throw new RuntimeException("unable to delete dir " + misDir.absolutePath)
                    }
                    misDir.mkdirs()
                }
            }

            project.repositories {
                flatDir {
                    dirs misDir.absolutePath
                }
            }

            publicationManager = PublicationManager.getInstance()
            publicationManager.loadManifest(project.rootProject, executePublish)

            misExtension = project.extensions.create('mis', MisExtension, new OnMisExtensionListener() {
                @Override
                void onPublicationAdded(Project childProject, Publication publication) {
                    initPublication(childProject, publication)

                    if (publication.version != null) {
                        handleMavenJar(childProject, publication)
                    } else {
                        handleLocalJar(childProject, publication)
                    }
                    publicationManager.hitPublication(publication)
                }
            })

            project.childProjects.each {
                Project childProject = it.value
                childProject.plugins.whenObjectAdded {
                    if (it instanceof AppPlugin || it instanceof LibraryPlugin) {
                        childProject.pluginManager.apply('mis')
                        childProject.repositories {
                            flatDir {
                                dirs misDir.absolutePath
                            }
                        }
                    }
                }
            }

            project.afterEvaluate {

                androidJarPath = MisUtil.getAndroidJarPath(project, misExtension.compileSdkVersion)

                project.childProjects.each {
                    Project childProject = it.value
                    def misScript = childProject.file('mis.gradle')
                    if (misScript.exists()) {
                        misExtension.childProject = childProject
                        project.apply from: misScript
                    }
                }

            }
            return
        }

        if (!MisUtil.hasAndroidPlugin(project)) {
            throw new GradleException("The android or android-library plugin must be applied to the project.")
        }

        project.gradle.getStartParameter().taskNames.each {
            if (it.startsWith('publishMis')) {
                executePublish = true
                publishPublication = it.substring(it.indexOf('[') + 1, it.lastIndexOf(']'))
            }
        }

        this.publicationPublishMap = new HashMap<>()

        Dependencies.metaClass.misPublication { String value ->
            String[] gav = filterGAV(value)
            return getPublication(gav)
        }

        project.dependencies.metaClass.misPublication { Object value ->
            def result = []
            if (executePublish) {
                return result
            }
            String[] gav = filterGAV(value)
            return getPublication(gav)
        }

        if (project.gradle.startParameter.taskNames.isEmpty()) {
            List<Publication> publications = publicationManager.getPublicationByProject(project)
            publications.each {
                addPublicationDependencies(it)
            }
        }

        project.afterEvaluate {
            MisUtil.addMisSourceSets(project)

            if (publicationPublishMap.size() == 0) {
                return
            }

            project.plugins.apply('maven-publish')
            def publishing = project.extensions.getByName('publishing')
            if (misExtension.configure != null) {
                publishing.repositories misExtension.configure
            }

            publicationPublishMap.each {
                createPublishTask(it.value)
            }
        }
    }

    String[] filterGAV(Object value) {
        String groupId = null, artifactId = null, version = null
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

        if (version == "") {
            version = null
        }

        if (groupId == null || artifactId == null) {
            throw new IllegalArgumentException("'${value}' is illege argument of misPublication(), the following types/formats are supported:" +
                    "\n  - String or CharSequence values, for example 'org.gradle:gradle-core:1.0'." +
                    "\n  - Maps, for example [groupId: 'org.gradle', artifactId: 'gradle-core', version: '1.0'].")
        }

        return [groupId, artifactId, version]
    }

    def handleLocalJar(Project project, Publication publication) {
        File target = new File(misDir, "mis-${publication.groupId}-${publication.artifactId}.jar")

        if (publication.invalid) {
            publicationManager.addPublication(publication)
            if (target.exists()) {
                target.delete()
            }
            return
        }

        if (target.exists()) {
            boolean hasModifiedSource = publicationManager.hasModified(publication)
            if (!hasModifiedSource) {
                publication.invalid = false
                publication.useLocal = true
                publicationManager.addPublication(publication)
                return
            }
        }

        File releaseJar = JarUtil.packJavaSourceJar(project, publication, androidJarPath, misExtension.compileOptions, true)
        if (releaseJar == null) {
            publication.invalid = true
            publicationManager.addPublication(publication)
            if (target.exists()) {
                target.delete()
            }
            return
        }

        MisUtil.copyFile(releaseJar, target)
        publication.invalid = false
        publication.useLocal = true
        publicationManager.addPublication(publication)
    }

    def handleMavenJar(Project project, Publication publication) {
        File target = new File(misDir, "mis-${publication.groupId}-${publication.artifactId}.jar")
        if (publication.invalid) {
            publicationManager.addPublication(publication)
            if (target.exists()) {
                target.delete()
            }
            return
        }

        boolean hasModifiedSource = publicationManager.hasModified(publication)

        if (target.exists()) {
            if (!hasModifiedSource) {
                publication.invalid = false
                publication.useLocal = true
                publicationManager.addPublication(publication)
                return
            }
        } else if (!hasModifiedSource) {
            publication.invalid = false
            publication.useLocal = false
            publicationManager.addPublication(publication)
            return
        }

        def releaseJar = JarUtil.packJavaSourceJar(project, publication, androidJarPath, misExtension.compileOptions, false)
        if (releaseJar == null) {
            publication.invalid = true
            publicationManager.addPublication(publication)
            if (target.exists()) {
                target.delete()
            }
            return
        }

        boolean equals = JarUtil.compareMavenJar(project, publication, releaseJar.absolutePath)
        if (equals) {
            if (target.exists()) {
                target.delete()
            }
            publication.useLocal = false
        } else {
            releaseJar = JarUtil.packJavaSourceJar(project, publication, androidJarPath, misExtension.compileOptions, true)
            MisUtil.copyFile(releaseJar, target)
            publication.useLocal = true
        }
        publication.invalid = false
        publicationManager.addPublication(publication)
    }

    void initPublication(Project project, Publication publication) {
        String displayName = project.getDisplayName()
        publication.project = displayName.substring(displayName.indexOf("'") + 1, displayName.lastIndexOf("'"))
        def buildMis = new File(project.projectDir, 'build/mis')

        publication.sourceSetName = publication.name
        publication.buildDir = new File(buildMis, publication.name)

        SourceSet misSourceSet = new SourceSet()
        def misDir
        if (publication.sourceSetName.contains('/')) {
            misDir = project.file(publication.sourceSetName + '/mis/')
        } else {
            misDir = project.file('src/' + publication.sourceSetName + '/mis/')
        }
        misSourceSet.path = misDir.absolutePath
        misSourceSet.lastModifiedSourceFile = new HashMap<>()
        project.fileTree(misDir).each {
            if (it.name.endsWith('.java') || it.name.endsWith('.kt')) {
                SourceFile sourceFile = new SourceFile()
                sourceFile.path = it.path
                sourceFile.lastModified = it.lastModified()
                misSourceSet.lastModifiedSourceFile.put(sourceFile.path, sourceFile)
            }
        }

        publication.misSourceSet = misSourceSet
        publication.invalid = misSourceSet.lastModifiedSourceFile.isEmpty()
    }

    def getPublication(String[] gav) {
        Publication publication = publicationManager.getPublication(gav[0], gav[1])
        if (publication != null) {
            if (publication.invalid) {
                return []
            } else if (publication.useLocal) {
                return ":mis-${publication.groupId}-${publication.artifactId}:"
            } else {
                return "${publication.groupId}:${publication.artifactId}:${publication.version}"
            }
        } else {
            return []
        }
    }

    void addPublicationDependencies(Publication publication) {
        if (publication.dependencies == null) return
        project.dependencies {
            if (publication.dependencies.compileOnly != null) {
                publication.dependencies.compileOnly.each {
                    compileOnly it
                }
            }
            if (publication.dependencies.implementation != null) {
                publication.dependencies.implementation.each {
                    implementation it
                }
            }
        }
    }

    void createPublishTask(Publication publication) {
        def taskName = 'compileMis[' + publication.artifactId + ']Source'
        def compileTask = project.getTasks().findByName(taskName)
        if (compileTask == null) {
            compileTask = project.getTasks().create(taskName, CompileMisTask.class)
            compileTask.publication = publication
            compileTask.dependsOn 'clean'
        }

        def publicationName = 'Mis[' + publication.artifactId + "]"
        String publishTaskNamePrefix = "publish" + publicationName + "PublicationTo"
        project.tasks.whenTaskAdded {
            if (it.name.startsWith(publishTaskNamePrefix)) {
                it.dependsOn compileTask
                it.doLast {
                    new File(misDir, "mis-${publication.groupId}-${publication.artifactId}.jar").delete()
                }
            }
        }
        createPublishingPublication(publication, publicationName)
    }

    void createPublishingPublication(Publication publication, String publicationName) {
        def publishing = project.extensions.getByName('publishing')
        MavenPublication mavenPublication = publishing.publications.maybeCreate(publicationName, MavenPublication)
        mavenPublication.groupId = publication.groupId
        mavenPublication.artifactId = publication.artifactId
        mavenPublication.version = publication.version
        mavenPublication.pom.packaging = 'jar'

        def outputsDir = new File(publication.buildDir, "outputs")
        mavenPublication.artifact source: new File(outputsDir, "classes.jar")
        mavenPublication.artifact source: new File(outputsDir, "classes-source.jar"), classifier: 'sources'

        if (publication.dependencies != null) {
            mavenPublication.pom.withXml {
                def dependenciesNode = asNode().appendNode('dependencies')
                if (publication.dependencies.implementation != null) {
                    publication.dependencies.implementation.each {
                        def gav = it.split(":")
                        if (gav[1].startsWith('mis-')) {
                            Publication dependencyPublication = publicationManager.getPublicationByKey(gav[1].replace('mis-', ''))
                            if (dependencyPublication.useLocal) {
                                throw new RuntimeException("mis publication [$dependencyPublication.groupId:$dependencyPublication.artifactId] has not publish yet.")
                            }
                        }
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