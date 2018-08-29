package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipFile

class JarUtil {

    static File packJavaSourceJar(Project project, Map<String, ?> options) {
        def typeDir = Util.getTypeDir(project, options)
        typeDir.deleteDir()
        typeDir.mkdirs()
        def sourceDir = new File(typeDir, "source")
        sourceDir.mkdirs()
        def classesDir = new File(typeDir, "classes")
        classesDir.mkdirs()
        def outputsDir = new File(typeDir, "outputs")
        outputsDir.mkdirs()

        boolean isMicroModule = Util.isMicroModule(project)
        def argFiles = []
        BaseExtension android = project.extensions.getByName('android')
        def target = android.compileOptions.targetCompatibility.getName()
        def source = android.compileOptions.sourceCompatibility.getName()

        def main = android.sourceSets.getByName('main')
        main.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (isMicroModule) {
                if (options.microModuleName == null) {
                    filterJavaSource(it, it.absolutePath, sourceDir, argFiles, options.sourceFilters)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    filterJavaSource(it, it.absolutePath, sourceDir, argFiles, options.sourceFilters)
                }
            } else {
                filterJavaSource(it, it.absolutePath, sourceDir, argFiles, options.sourceFilters)
            }
        }

        if (argFiles.size() == 0) {
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
            if (it.name.endsWith('.aar')) {
                classPath << getAARClassesJar(it)
            } else {
                classPath << it.absolutePath
            }
        }
        classPath << project.android.bootClasspath[0].toString()

        return generateJavaSourceJar(classesDir, argFiles, classPath, target, source)
    }

    static File packJavaDocSourceJar(Project project, Map<String, ?> options) {
        def typeDir = Util.getTypeDir(project, options)
        def javaSource = new File(typeDir, "javaSource")
        javaSource.deleteDir()
        javaSource.mkdirs()

        boolean isMicroModule = Util.isMicroModule(project)
        BaseExtension android = project.extensions.getByName('android')
        def main = android.sourceSets.getByName('main')
        main.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (isMicroModule) {
                if (options.microModuleName == null) {
                    filterJavaDocSource(it, it.absolutePath, javaSource)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    filterJavaDocSource(it, it.absolutePath, javaSource)
                }
            } else {
                filterJavaDocSource(it, it.absolutePath, javaSource)
            }
        }

        return generateJavaDocSourceJar(javaSource)
    }

    static boolean compareMavenJar(Project project, Map<String, ?> options, String localPath) {
        Map<String, ?> optionsCopy = Util.optionsFilter(options)
        String filePath = null
        String fileName = options.artifactId + "-" + options.version + ".jar"
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

    private static File generateJavaSourceJar(File classesDir, def argFiles, def classPath, def target, def source) {
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

    private static void filterJavaSource(File file, String prefix, File sourceDir, def argFiles, Closure[] sourceFilters) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterJavaSource(childFile, prefix, sourceDir, argFiles, sourceFilters)
            }
        } else {
            if (file.name.endsWith(".java")) {
                def packageName = file.parent.replace(prefix, "")
                def targetParent = new File(sourceDir, packageName)
                if (!targetParent.exists()) targetParent.mkdirs()
                def target = new File(targetParent, file.name)
                Util.copyFile(file, target)
                argFiles << target.absolutePath

                if (sourceFilters != null) {
                    sourceFilters.each {
                        it.call(target)
                    }
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
                Util.copyFile(file, target)
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