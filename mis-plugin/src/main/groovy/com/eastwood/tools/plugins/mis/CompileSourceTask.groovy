package com.eastwood.tools.plugins.mis

import com.eastwood.tools.plugins.mis.extension.MisSource
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CompileSourceTask extends DefaultTask {

    MisSource misSource

    @TaskAction
    void upload() {
        def project = getProject()

        def typeDir = Util.getTypeDir(project, misSource)
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def releaseJar = JarUtil.packJavaSourceJar(project, misSource)
        if (releaseJar == null) {
            throw new RuntimeException("nothing to push.")
        }
        JarUtil.packJavaDocSourceJar(project, misSource)

        SourceStateUtil.updateSourceFileState(project, misSource)
    }

}