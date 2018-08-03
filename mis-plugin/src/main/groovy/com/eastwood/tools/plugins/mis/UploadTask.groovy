package com.eastwood.tools.plugins.mis

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

class UploadTask extends DefaultTask {

    @TaskAction
    void upload() {

        println getDependsOn()
    }

}