package com.eastwood.tools.plugins.mis

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CompileMisSourceTask extends DefaultTask {

    Map<String, ?> options

    @TaskAction
    void upload() {
        def project = getProject()

        def typeDir = JarPacker.getTypeDir(project, options)
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def releaseJar = JarPacker.packReleaseJar(project, options)
        if (releaseJar == null) {
            throw new RuntimeException("nothing to push.")
        }
        JarPacker.packJavaSourceJar(project, options)
    }

}