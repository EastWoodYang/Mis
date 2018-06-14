package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project

class JarPacker {

    static File packJavaSourceJar(Project project, Map<String, ?> options) {
        def typeDir = getTypeDir(project, options)
        def javaSource = new File(typeDir, "javaSource")
        javaSource.deleteDir()
        javaSource.mkdirs()

        boolean isMicroModule = MisUtil.isMicroModule(project)
        BaseExtension android = project.extensions.getByName('android')
        def main = android.sourceSets.getByName('main')
        main.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (isMicroModule) {
                if (options.microModuleName == null) {
                    filterJavaSource(it, it.absolutePath, javaSource)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    filterJavaSource(it, it.absolutePath, javaSource)
                }
            } else {
                filterJavaSource(it, it.absolutePath, javaSource)
            }
        }

        return generateJavaSourceJar(javaSource)
    }

    static File packReleaseJar(Project project, Map<String, ?> options) {
        def releaseJar = getReleaseJar(project, options)
        def typeDir = getTypeDir(project, options)
        def lastModifiedManifest = new File(typeDir, "lastModifiedManifest.xml")
        if (releaseJar.exists() && lastModifiedManifest.exists()) {
            if (!hasModifiedSource(project, options)) {
                return releaseJar
            }
        }

        typeDir.deleteDir()
        typeDir.mkdirs()
        def sourceDir = new File(typeDir, "source")
        sourceDir.mkdirs()
        def classesDir = new File(typeDir, "classes")
        classesDir.mkdirs()
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        boolean isMicroModule = MisUtil.isMicroModule(project)
        def argFiles = []
        Map<String, SourceFile> currentModifiedSourceFileMap = new HashMap<>()

        BaseExtension android = project.extensions.getByName('android')
        def target = android.compileOptions.targetCompatibility.getName()
        def source = android.compileOptions.sourceCompatibility.getName()

        def main = android.sourceSets.getByName('main')
        main.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (isMicroModule) {
                if (options.microModuleName == null) {
                    filterSource(it, it.absolutePath, sourceDir, argFiles, currentModifiedSourceFileMap, options.sourceFilters)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    filterSource(it, it.absolutePath, sourceDir, argFiles, currentModifiedSourceFileMap, options.sourceFilters)
                }
            } else {
                filterSource(it, it.absolutePath, sourceDir, argFiles, currentModifiedSourceFileMap, options.sourceFilters)
            }
        }

        if(argFiles.size() == 0) {
            return null
        }

        def random = new Random();
        def name = "mis_" + random.nextLong()
        project.configurations.create(name)
        options.dependencies.each {
            project.dependencies.add(name, it)
        }
        def classPath = []
        project.configurations.getByName(name).resolve().each {
            classPath << it
        }
        classPath << project.android.bootClasspath[0].toString()

        releaseJar = generateReleaseJar(classesDir, argFiles, classPath, target, source)
        MisUtil.saveCurrentModifiedManifest(lastModifiedManifest, currentModifiedSourceFileMap)
        return releaseJar
    }

    static File getReleaseJar(Project project, Map<String, ?> options) {
        def typeDir = getTypeDir(project, options)
        return new File(typeDir, "outputs/classes.jar")
    }

    static File getTypeDir(Project project, Map<String, ?> options) {
        boolean isMicroModule = MisUtil.isMicroModule(project)
        def misRoot = new File(project.projectDir, 'build/mis')
        def typeDir = new File(misRoot, "main")
        if (isMicroModule && options.microModuleName != null) {
            typeDir = new File(misRoot, options.microModuleName)
        }
        return typeDir
    }



    private static File generateReleaseJar(File classesDir, def argFiles, def classPath, def target, def source) {
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
        p.destroy()
        p = null
        if (result != 0) {
            throw new RuntimeException("failure to package classes.jar: \n" + p.err.text)
        }

        return new File(classesDir.parentFile, 'outputs/classes.jar')
    }

    private static File generateJavaSourceJar(File sourceDir) {
        def p = "jar cvf ../outputs/classes-source.jar .".execute(null, sourceDir)
        def result = p.waitFor()
        if (result != 0) {
            throw new RuntimeException("failure to make mis-sdk java source directory: \n" + p.err.text)
        }
        def sourceJar = new File(sourceDir.parentFile, 'outputs/classes-source.jar')
        return sourceJar
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


    private static void filterSource(File file, String prefix, File sourceDir, def argFiles, Map<String, SourceFile> currentModifiedSourceFileMap, Closure[] sourceFilters) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterSource(childFile, prefix, sourceDir, argFiles, currentModifiedSourceFileMap, sourceFilters)
            }
        } else {
            if (file.name.endsWith(".java")) {
                SourceFile sourceFile = new SourceFile()
                sourceFile.name = file.name
                sourceFile.path = file.absolutePath
                sourceFile.lastModified = file.lastModified()
                currentModifiedSourceFileMap.put(sourceFile.path, sourceFile)

                def packageName = file.parent.replace(prefix, "")
                def targetParent = new File(sourceDir, packageName)
                if (!targetParent.exists()) targetParent.mkdirs()
                def target = new File(targetParent, file.name)
                MisUtil.copyFile(file, target)
                argFiles << target.absolutePath

                if (sourceFilters != null) {
                    sourceFilters.each {
                        it.call(target)
                    }
                }
            }
        }
    }

    private static void filterJavaSource(File file, String prefix, File javaDocDir) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterJavaSource(childFile, prefix, javaDocDir)
            }
        } else {
            if (file.name.endsWith(".java")) {
                def packageName = file.parent.replace(prefix, "")
                def targetParent = new File(javaDocDir, packageName)
                if (!targetParent.exists()) targetParent.mkdirs()
                def target = new File(targetParent, file.name)
                MisUtil.copyFile(file, target)
            }
        }
    }

    static boolean hasModifiedSource(Project project, Map<String, ?> options) {
        def typeDir = getTypeDir(project, options)
        def lastModifiedManifest = new File(typeDir, "lastModifiedManifest.xml")
        if(!lastModifiedManifest.exists()) {
            return true
        }
        Map<String, SourceFile> lastModifiedSourceFileMap = MisUtil.getLastModifiedSourceFileMap(lastModifiedManifest)

        boolean isMicroModule = MisUtil.isMicroModule(project)
        BaseExtension android = project.extensions.getByName('android')
        def main = android.sourceSets.getByName('main')
        for (File it : main.aidl.srcDirs) {
            if (!it.absolutePath.endsWith("mis")) continue

            boolean result = false
            if (isMicroModule) {
                if (options.microModuleName == null) {
                    result = findModifiedSource(it, lastModifiedSourceFileMap)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    result = findModifiedSource(it, lastModifiedSourceFileMap)
                }
            } else {
                result = findModifiedSource(it, lastModifiedSourceFileMap)
            }
            if (result) {
                return true
            }
        }
        return lastModifiedSourceFileMap.size() > 0
    }

    private static boolean findModifiedSource(File file, Map<String, SourceFile> lastModifiedSourceFileMap) {
        if (file.isDirectory()) {
            for (File childFile : file.listFiles()) {
                boolean result = findModifiedSource(childFile, lastModifiedSourceFileMap)
                if (result) {
                    return true
                }
            }
            return false
        } else {
            if (file.name.endsWith(".java")) {
                SourceFile sourceFile = lastModifiedSourceFileMap.get(file.absolutePath)
                if (sourceFile == null) {
                    return true
                }

                if (file.lastModified() != sourceFile.lastModified) {
                    return true
                } else {
                    lastModifiedSourceFileMap.remove(file.absolutePath)
                    return false
                }
            } else {
                return false
            }
        }
    }


}