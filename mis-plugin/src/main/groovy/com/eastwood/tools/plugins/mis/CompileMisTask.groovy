package com.eastwood.tools.plugins.mis

import com.eastwood.tools.plugins.mis.core.JarUtil
import com.eastwood.tools.plugins.mis.core.MisUtil
import com.eastwood.tools.plugins.mis.core.extension.MisSource
import com.eastwood.tools.plugins.mis.core.state.StateUtil
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class CompileMisTask extends DefaultTask {

    MisSource misSource

    @TaskAction
    void compileSource() {
        def project = getProject()

        def typeDir = MisUtil.getTypeDir(project, misSource)
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def releaseJar = JarUtil.packJavaSourceJar(project, misSource)
        if (releaseJar == null) {
            throw new RuntimeException("nothing to push.")
        }
        JarUtil.packJavaDocSourceJar(project, misSource)

        StateUtil.updateSourceFileState(project, misSource)
    }

}