package com.eastwood.tools.plugins.mis

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.client.HttpResponseException
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.apache.http.entity.mime.content.FileBody
import org.apache.http.entity.mime.content.StringBody
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class UploadTask extends DefaultTask {

    MisMavenExtension mavenExtension
    Map<String, ?> options
    boolean isMicroModule

    @TaskAction
    void upload() {
        def project = getProject()
        isMicroModule = MisUtil.isMicroModule(project)
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

        def typeDir = JarPacker.getTypeDir(project, options)
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def releaseJar = JarPacker.packReleaseJar(project, options)
        if (releaseJar == null) {
            throw new RuntimeException("nothing to push.")
        }
        def sourceJar = JarPacker.packJavaSourceJar(project, options)
        def pomFile = createPom(outputsDir)
        uploadArtifact(releaseJar, sourceJar, pomFile)

        File group = project.rootProject.file(".gradle/mis/" + options.group)
        new File(group, options.name + ".jar").delete()
        releaseJar.delete()
    }

    def createPom(File outputsDir) {
        def pom = '<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">'
        pom += '<modelVersion>4.0.0</modelVersion>'
        pom += '<groupId>' + options.group + '</groupId>'
        pom += '<artifactId>' + options.name + '</artifactId>'
        pom += '<version>' + options.version + '</version>'
        pom += '<packaging>jar</packaging>'
        pom += '<licenses><license><name>The Apache Software License, Version 2.0</name><url>http://www.apache.org/licenses/LICENSE-2.0.txt</url></license></licenses>'
        if (options.dependencies != null && options.dependencies.size() > 0) {
            pom += '<dependencies>'
            options.dependencies.each {
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
                        println "\nupload ${options.group}:${options.name}:${options.version} success!"

                        project.logger.error("\nNow you need to specify a version or update version of ${options.group}:${options.name} in misProvider, Or you'll get a hint, look like:  \n" +
                                "> Failed to transform file '${options.name}.jar' to match attributes {artifactType=android-classes} using transform JarTransform\n" +
                                "   > Transform output file ...${options.name}.jar does not exist.")
                    }
                }
            }
        } catch (HttpResponseException e) {
            if (e.message.contains("Bad Request")) {
                throw new IllegalArgumentException("[${options.group}:${options.name}:${options.version}] may already exists.\n" + e.toString())
            } else {
                throw e
            }
        }
    }

}