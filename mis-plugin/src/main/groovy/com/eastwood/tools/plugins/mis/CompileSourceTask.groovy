package com.eastwood.tools.plugins.mis

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CompileSourceTask extends DefaultTask {

    Map<String, ?> options

    @TaskAction
    void upload() {
        def project = getProject()

        def typeDir = Util.getTypeDir(project, options)
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def releaseJar = JarUtil.packJavaSourceJar(project, options)
        if (releaseJar == null) {
            throw new RuntimeException("nothing to push.")
        }
        JarUtil.packJavaDocSourceJar(project, options)

        SourceStateUtil.updateSourceFileState(project, options)
    }

}