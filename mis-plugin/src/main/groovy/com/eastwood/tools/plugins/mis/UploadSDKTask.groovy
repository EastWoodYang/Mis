package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.client.HttpResponseException
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class UploadSDKTask extends DefaultTask {

    MisMavenExtension mavenExtension
    MisUploadConfig uploadConfig
    boolean isMicroModule
    def classPath = []

    @TaskAction
    void upload() {
        def project = getProject()

        mavenExtension = project.extensions.findByName("misMaven")
        if (mavenExtension == null) {
            mavenExtension = project.rootProject.extensions.findByName("misMaven")
            if (mavenExtension == null) {
                def tip = 'you should add configuration:\n\napply plugin: \'mis-maven\'\n\nmisMaven {\n       url = ...\n       repository = ...\n       username = ...\n       password = ...\n}\n\nin [build.gradle] or root project [build.gralde]'
                throw new IllegalArgumentException(tip)
            }
        }

        if (mavenExtension.url == null) {
            throw new IllegalArgumentException("maven url is not set yet.\n")
        } else if (mavenExtension.repository == null) {
            throw new IllegalArgumentException("maven repository is not set yet.\n")
        }

        def tip = 'you should add configuration:\nuploadMis {\n ' + uploadConfig.name + '{\ngroupId = ...\nartifactId = ...\nversion = ...\n}\n}'
        if (uploadConfig.groupId == null) {
            throw new IllegalArgumentException("mis-sdk GAV option of version is not set yet.\n" + tip)
        } else if (uploadConfig.artifactId == null) {
            throw new IllegalArgumentException("mis-sdk GAV option of artifactId is not set yet.\n" + tip)
        } else if (uploadConfig.version == null) {
            throw new IllegalArgumentException("mis-sdk GAV option of version is not set yet.\n" + tip)
        }

        isMicroModule = isMicroModule(project)

        project.configurations.create("mis")
        uploadConfig.dependencies.each {
            project.dependencies.add("mis", it)
        }
        project.configurations.mis.resolve().each {
            classPath << it
        }
        classPath << project.android.bootClasspath[0].toString()

        println "----"
        if (classPath.size() == 0) {
            println '- classpath: default'
        } else {
            println '- classpath:'
            classPath.each {
                println '-- ' + it
            }
        }

        def misRoot = new File(project.projectDir, 'build/mis')
        def typeDir = new File(misRoot, uploadConfig.name)
        typeDir.deleteDir()
        typeDir.mkdirs()
        def sourceDir = new File(typeDir, "source")
        sourceDir.mkdirs()
        def javaSource = new File(typeDir, "javaSource")
        javaSource.mkdirs()
        def classesDir = new File(typeDir, "classes")
        classesDir.mkdirs()
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def argFiles = []
        BaseExtension android = project.extensions.getByName('android')
        def main = android.sourceSets.getByName('main')
        main.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (isMicroModule && !uploadConfig.ignoreMicroModule) {
                if (it.absolutePath.endsWith(uploadConfig.name + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    filterSource(it, it.absolutePath, sourceDir, argFiles)
                    filterJavaDoc(it, it.absolutePath, javaSource)
                }
            } else {
                filterSource(it, it.absolutePath, sourceDir, argFiles)
                filterJavaDoc(it, it.absolutePath, javaSource)
            }

        }

        if (argFiles.size() == 0) {
            throw new RuntimeException("can't find any file under [mis]")
        } else {
            println '\n- api file:'
            argFiles.each {
                println '-- ' + it
            }
        }

        def releaseJar = packageReleaseJar(classesDir, argFiles)
        def sourceJar = packageSourceJar(javaSource)
        def pomFile = createPom(outputsDir)
        uploadArtifact(releaseJar, sourceJar, pomFile)
    }

    def filterSource(File file, String prefix, File sourceDir, def argFiles) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterSource(childFile, prefix, sourceDir, argFiles)
            }
        } else {
            if (file.name.endsWith(".java")) {
                def packageName = file.parent.replace(prefix, "")
                def targetParent = new File(sourceDir, packageName)
                if (!targetParent.exists()) targetParent.mkdirs()
                def target = new File(targetParent, file.name)
                copyFile(file, target)
                argFiles << target.absolutePath

                if (uploadConfig.sourceFilters != null) {
                    uploadConfig.sourceFilters.each {
                        it.call(target)
                    }
                }
            }
        }
    }

    def filterJavaDoc(File file, String prefix, File javaDocDir) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterJavaDoc(childFile, prefix, javaDocDir)
            }
        } else {
            if (file.name.endsWith(".java")) {
                def packageName = file.parent.replace(prefix, "")
                def targetParent = new File(javaDocDir, packageName)
                if (!targetParent.exists()) targetParent.mkdirs()
                def target = new File(targetParent, file.name)
                copyFile(file, target)
            }
        }
    }

    def copyFile(File source, File target) {
        def line, text = ''
        source.withReader('utf-8') { reader ->
            while ((line = reader.readLine()) != null) {
                text += line + '\n'
            }
        }

        if (target.exists()) target.delete()
        target.withWriter('utf-8') {
            it.write text
        }
    }

    def isMicroModule(Project project) {
        def result = project.extensions.findByName('microModule')
        return result != null
    }

    def packageReleaseJar(File classesDir, def argFiles) {
        BaseExtension android = project.extensions.getByName('android')
        def target = android.compileOptions.targetCompatibility.getName()
        def source = android.compileOptions.sourceCompatibility.getName()
        def p
        if (classPath.size() == 0) {
            p = ("javac -encoding UTF-8 -target " + target + " -source " + source + " -d . " + argFiles.join(' ')).execute(null, classesDir)
        } else {
            p = ("javac -encoding UTF-8 -target " + target + " -source " + source + " -d . -classpath " + classPath.join(';') + " " + argFiles.join(' ')).execute(null, classesDir)
        }

        def result = p.waitFor()
        if (result != 0) {
            throw new RuntimeException("Failure to convert java source to bytecode: \n" + p.err.text)
        }

        p = "jar cvf outputs/classes.jar -C classes . ".execute(null, classesDir.parentFile)
        result = p.waitFor()
        if (result != 0) {
            throw new RuntimeException("failure to package classes.jar: \n" + p.err.text)
        }

        def classesJar = new File(classesDir.parentFile, 'outputs/classes.jar')
        println '\n- output: '
        println '-- ' + classesJar.absolutePath
        return classesJar
    }

    def packageSourceJar(File sourceDir) {
        def p = "jar cvf ../outputs/classes-source.jar .".execute(null, sourceDir)
        def result = p.waitFor()
        if (result != 0) {
            throw new RuntimeException("failure to make mis-sdk java source directory: \n" + p.err.text)
        }
        def sourceJar = new File(sourceDir.parentFile, 'outputs/classes-source.jar')
        println '-- ' + sourceJar.absolutePath
        return sourceJar
    }

    def createPom(File outputsDir) {
        def pom = '<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">'
        pom += '<modelVersion>4.0.0</modelVersion>'
        pom += '<groupId>' + uploadConfig.groupId + '</groupId>'
        pom += '<artifactId>' + uploadConfig.artifactId + '</artifactId>'
        pom += '<version>' + uploadConfig.version + '</version>'
        pom += '<packaging>jar</packaging>'
        pom += '<licenses><license><name>The Apache Software License, Version 2.0</name><url>http://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses>'
        if (uploadConfig.dependencies != null && uploadConfig.dependencies.size() > 0) {
            pom += '<dependencies>'
            uploadConfig.dependencies.each {
                def dependency = it.split(':')
                pom += '<dependency>\n'
                pom += '<groupId>' + dependency[0] + '</groupId>'
                pom += '<artifactId>' + dependency[1] + '</artifactId>'
                pom += '<version>' + dependency[2] + '</version>'
                pom += '<scope>compile</scope>'
                pom += '</dependency>'
            }
            pom += '</dependencies>'
        }
        pom += '</project>'
        def pomFile = new File(outputsDir, 'pom.xml')
        pomFile.write(pom)
        return pomFile
    }

    def uploadArtifact(File releaseJar, File sourceJar, File pomFile) {
        HTTPBuilder http = new HTTPBuilder("${mavenExtension.url}/service/local/artifact/maven/content")
        if (mavenExtension.username != null && mavenExtension.password != null) {
            http.auth.basic(mavenExtension.username, mavenExtension.password)
        }
        try {
            http.request(Method.POST, ContentType.ANY) { req ->
                MultipartEntityBuilder entityBuilder = new MultipartEntityBuilder()
                entityBuilder.addPart("r", new StringBody(mavenExtension.repository))
                entityBuilder.addPart("hasPom", new StringBody("true"))
                entityBuilder.addPart("file", new FileBody(pomFile))
                entityBuilder.addPart("e", new StringBody("jar"))
                entityBuilder.addPart("file", new FileBody(releaseJar))
                entityBuilder.addPart("c", new StringBody("sources"))
                entityBuilder.addPart("file", new FileBody(sourceJar))
                req.entity = entityBuilder.build()
                response.success = { resp, reader ->
                    if (resp.status == 201) {
                        if (isMicroModule && !uploadConfig.ignoreMicroModule) {
                            println "\nupload mis-sdk of microModule:" + uploadConfig.name + " under " + project.displayName + " success!"
                        } else {
                            println "\nupload mis-sdk of " + project.displayName + " success!"
                        }
                    }
                }
            }
        } catch (HttpResponseException e) {
            if (e.message.contains("Bad Request")) {
                throw new IllegalArgumentException("[${uploadConfig.groupId}:${uploadConfig.artifactId}:${uploadConfig.version}] may already exists.\n" + e.toString())
            } else {
                throw e
            }
        }
    }

}