package com.eastwood.tools.plugins.mis

import org.gradle.api.Project
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.zip.ZipFile

class MisUtil {

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
        Map<String, ?> optionsCopy = options.clone()
        optionsCopy.remove("dependencies")
        optionsCopy.remove("microModuleName")
        return optionsCopy
    }

    static boolean compareMavenJar(Project project, Map<String, ?> options, String localPath) {
        Map<String, ?> optionsCopy = optionsFilter(options)

        String filePath = null
        String fileName = optionsCopy.name + "-" + optionsCopy.version + ".jar"

        def random = new Random()
        def name = "mis_" + random.nextLong()
        project.configurations.create(name)
        project.dependencies.add(name, optionsCopy)
        project.configurations.getByName(name).resolve().each {
            if (it.name.endsWith(fileName)) {
                filePath = it.absolutePath
            }
        }

        return compareJar(localPath, filePath)
    }

    static boolean compareJar(String jar1, String jar2) {
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

    static String getAARClassesJar(File input) {
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

    static SourceState getLastModifiedSourceState(File lastModifiedManifestFile) {
        SourceState source = new SourceState()
        source.lastModifiedSourceFile = new HashMap<>()
        if (!lastModifiedManifestFile.exists()) return source
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document document = builderFactory.newDocumentBuilder().parse(lastModifiedManifestFile)
        NodeList classesNodeList = document.getElementsByTagName("source")
        if (classesNodeList.length == 0) {
            return source.lastModifiedSourceFile
        }
        Element classesElement = (Element) classesNodeList.item(0)
        source.version = classesElement.getAttribute("version")
        NodeList fileNodeList = classesElement.getElementsByTagName("file")
        for (int i = 0; i < fileNodeList.getLength(); i++) {
            Element fileElement = (Element) fileNodeList.item(i)
            SourceFile resourceFile = new SourceFile()
            resourceFile.name = fileElement.getAttribute("name")
            resourceFile.path = fileElement.getAttribute("path")
            resourceFile.lastModified = fileElement.getAttribute("lastModified").toLong()
            source.lastModifiedSourceFile.put(resourceFile.path, resourceFile)
        }
        return source
    }

    static saveCurrentModifiedManifest(File manifestFile, String version, Map<String, SourceFile> currentModifiedSourceMap) {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document documentTemp = builderFactory.newDocumentBuilder().newDocument()
        // resources
        Element sourceElement = documentTemp.createElement("source")
        sourceElement.setAttribute("version", version)
        currentModifiedSourceMap.each {
            SourceFile sourceFile = it.value
            Element fileElement = documentTemp.createElement("file")
            fileElement.setAttribute("name", sourceFile.name)
            fileElement.setAttribute("path", sourceFile.path)
            fileElement.setAttribute("lastModified", sourceFile.lastModified.toString())
            sourceElement.appendChild(fileElement)
        }

        // save
        Transformer transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        transformer.transform(new DOMSource(sourceElement), new StreamResult(manifestFile))
    }
}