package com.eastwood.tools.plugins.mis.core

import com.android.build.gradle.BaseExtension
import com.eastwood.tools.plugins.mis.core.extension.MisSource
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipFile

class JarUtil {

    static File packJavaSourceJar(Project project, MisSource misSource) {
        def typeDir = MisUtil.getTypeDir(project, misSource)
        typeDir.deleteDir()
        typeDir.mkdirs()
        def sourceDir = new File(typeDir, "source")
        sourceDir.mkdirs()
        def classesDir = new File(typeDir, "classes")
        classesDir.mkdirs()
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        def argFiles = []

        BaseExtension android = project.extensions.getByName('android')
        def sourceSets = android.sourceSets.getByName(misSource.flavorName)
        sourceSets.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (misSource.microModuleName != null) {
                if (it.absolutePath.endsWith(misSource.microModuleName + "${File.separator}src${File.separator + misSource.flavorName + File.separator}mis")) {
                    filterJavaSource(it, it.absolutePath, sourceDir, argFiles, misSource.sourceFilter)
                }
            } else {
                filterJavaSource(it, it.absolutePath, sourceDir, argFiles, misSource.sourceFilter)
            }
        }

        if (argFiles.size() == 0) {
            return null
        }

        def random = new Random();
        def name = "mis_" + random.nextLong()
        project.configurations.create(name)
        if(misSource.dependencies.implementation != null) {
            misSource.dependencies.implementation.each {
                project.dependencies.add(name, it)
            }
        }
        if(misSource.dependencies.compileOnly != null) {
            misSource.dependencies.compileOnly.each {
                project.dependencies.add(name, it)
            }
        }

        def classPath = []
        project.configurations.getByName(name).resolve().each {
            if (it.name.endsWith('.aar')) {
                classPath << getAARClassesJar(it)
            } else {
                classPath << it.absolutePath
            }
        }
        classPath << project.android.bootClasspath[0].toString()

        def target = android.compileOptions.targetCompatibility.versionName
        def source = android.compileOptions.sourceCompatibility.versionName
        return generateJavaSourceJar(classesDir, argFiles, classPath, target, source)
    }

    static File packJavaDocSourceJar(Project project, MisSource misSource) {
        def typeDir = MisUtil.getTypeDir(project, misSource)
        def javaSource = new File(typeDir, "javaSource")
        javaSource.deleteDir()
        javaSource.mkdirs()

        BaseExtension android = project.extensions.getByName('android')
        def sourceSets = android.sourceSets.getByName(misSource.flavorName)
        sourceSets.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (misSource.microModuleName != null) {
                if (it.absolutePath.endsWith(misSource.microModuleName + "${File.separator}src${File.separator + misSource.flavorName + File.separator}mis")) {
                    filterJavaDocSource(it, it.absolutePath, javaSource)
                }
            } else {
                filterJavaDocSource(it, it.absolutePath, javaSource)
            }
        }

        return generateJavaDocSourceJar(javaSource)
    }

    static boolean compareMavenJar(Project project, MisSource misSource, String localPath) {
        Map<String, ?> optionsCopy = MisUtil.optionsFilter(misSource)
        String filePath = null
        String fileName = misSource.artifactId + "-" + misSource.version + ".jar"
        def random = new Random()
        def name = "mis_" + random.nextLong()
        project.configurations.create(name)
        project.dependencies.add(name, optionsCopy)
        project.configurations.getByName(name).resolve().each {
            if (it.name.endsWith(fileName)) {
                filePath = it.absolutePath
            }
        }
        if (filePath == null) return false
        return compareJar(localPath, filePath)
    }

    private static File generateJavaSourceJar(File classesDir,
                                              def argFiles, def classPath, def target, def source) {
        def classpathSeparator = ";"
        if (!System.properties['os.name'].toLowerCase().contains('windows')) {
            classpathSeparator = ":"
        }
        def p
        if (classPath.size() == 0) {
            p = ("javac -encoding UTF-8 -target " + target + " -source " + source + " -d . " + argFiles.join(' ')).execute(null, classesDir)
        } else {
            p = ("javac -encoding UTF-8 -target " + target + " -source " + source + " -d . -classpath " + classPath.join(classpathSeparator) + " " + argFiles.join(' ')).execute(null, classesDir)
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

    private static File generateJavaDocSourceJar(File sourceDir) {
        def p = "jar cvf ../outputs/classes-source.jar .".execute(null, sourceDir)
        def result = p.waitFor()
        if (result != 0) {
            throw new RuntimeException("failure to make mis-sdk java source directory: \n" + p.err.text)
        }
        def sourceJar = new File(sourceDir.parentFile, 'outputs/classes-source.jar')
        return sourceJar
    }

    private static void filterJavaSource(File file, String prefix, File sourceDir,
                                         def argFiles, Closure sourceFilter) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterJavaSource(childFile, prefix, sourceDir, argFiles, sourceFilter)
            }
        } else {
            if (file.name.endsWith(".java")) {
                def packageName = file.parent.replace(prefix, "")
                def targetParent = new File(sourceDir, packageName)
                if (!targetParent.exists()) targetParent.mkdirs()
                def target = new File(targetParent, file.name)
                MisUtil.copyFile(file, target)
                argFiles << target.absolutePath

                if (sourceFilter != null) {
                    sourceFilter.call(target)
                }
            }
        }
    }

    private static void filterJavaDocSource(File file, String prefix, File javaDocDir) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterJavaDocSource(childFile, prefix, javaDocDir)
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

    private static boolean compareJar(String jar1, String jar2) {
        try {
            JarFile jarFile1 = new JarFile(jar1)
            JarFile jarFile2 = new JarFile(jar2)
            if (jarFile1.size() != jarFile2.size())
                return false

            Enumeration entries = jarFile1.entries()
            while (entries.hasMoreElements()) {
                JarEntry jarEntry1 = (JarEntry) entries.nextElement()
                if (!jarEntry1.name.endsWith(".class"))
                    continue

                JarEntry jarEntry2 = jarFile2.getJarEntry(jarEntry1.getName())
                if (jarEntry2 == null) {
                    return false
                }
                InputStream stream1 = jarFile1.getInputStream(jarEntry1)
                byte[] bytes1 = stream1.bytes
                bytes1 = Arrays.copyOfRange(bytes1, 8, bytes1.length)
                stream1.close()

                InputStream stream2 = jarFile2.getInputStream(jarEntry2)
                byte[] bytes2 = stream2.bytes
                bytes2 = Arrays.copyOfRange(bytes2, 8, bytes2.length)
                stream2.close()

                if (!Arrays.equals(bytes1, bytes2)) {
                    return false
                }
            }
            jarFile1.close()
            jarFile2.close()
        } catch (IOException e) {
            return false
        }
        return true
    }

    private static String getAARClassesJar(File input) {
        def jarFile = new File(input.getParent(), 'classes.jar')
        if (jarFile.exists()) return jarFile

        def zip = new ZipFile(input)
        zip.entries().each {
            if (it.isDirectory()) return
            if (it.name == 'classes.jar') {
                def fos = new FileOutputStream(jarFile)
                fos.write(zip.getInputStream(it).bytes)
                fos.close()
            }
        }
        zip.close()
        return jarFile.absolutePath
    }

}