package com.eastwood.tools.plugins.mis

import com.eastwood.tools.plugins.mis.core.JarUtil
import com.eastwood.tools.plugins.mis.core.extension.Publication

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CompileMisTask extends DefaultTask {

    Publication publication

    @TaskAction
    void compileSource() {
        def project = getProject()
        def releaseJar = JarUtil.packJavaSourceJar(project, publication)
        if (releaseJar == null) {
            throw new RuntimeException("nothing to push.")
        }
        JarUtil.packJavaDocSourceJar(project, publication)
    }

}