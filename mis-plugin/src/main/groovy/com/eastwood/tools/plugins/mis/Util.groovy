package com.eastwood.tools.plugins.mis

import org.gradle.api.Project;

class Util {

    static boolean isMicroModule(Project project) {
        return project.plugins.findPlugin("micro-module")
    }

    static boolean isAndroidPlugin(Project project) {
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            return true
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            return true
        } else {
            return false
        }
    }

    static Map<String, ?> optionsFilter(Map<String, ?> options) {
        Map<String, ?> opts = new HashMap<>()
        opts.put("group", options.groupId)
        opts.put("name", options.artifactId)
        opts.put("version", options.version)
        return opts
    }

    static void copyFile(File source, File target) {
        try {
            InputStream input = new FileInputStream(source)
            OutputStream output = new FileOutputStream(target)
            byte[] buf = new byte[1024]
            int bytesRead
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead)
            }
            input.close()
            output.close()
        } catch (IOException e) {
            e.printStackTrace()
        }
    }

    static File getTypeDir(Project project, Map<String, ?> options) {
        boolean isMicroModule = isMicroModule(project)
        def misRoot = new File(project.projectDir, 'build/mis')
        def typeDir = new File(misRoot, "main")
        if (isMicroModule && options.microModuleName != null) {
            typeDir = new File(misRoot, options.microModuleName)
        }
        return typeDir
    }
}
