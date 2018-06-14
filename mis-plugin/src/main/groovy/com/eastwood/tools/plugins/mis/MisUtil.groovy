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
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(target);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead)
            }
            input.close();
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static Map<String, SourceFile> getLastModifiedSourceFileMap(File lastModifiedManifestFile) {
        Map<String, SourceFile> lastModifiedSourceFileMap = new HashMap<>();
        if (!lastModifiedManifestFile.exists()) return
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document document = builderFactory.newDocumentBuilder().parse(lastModifiedManifestFile)
        NodeList classesNodeList = document.getElementsByTagName("source")
        if (classesNodeList.length == 0) {
            return lastModifiedSourceFileMap
        }
        Element classesElement = (Element) classesNodeList.item(0)
        NodeList fileNodeList = classesElement.getElementsByTagName("file")
        for (int i = 0; i < fileNodeList.getLength(); i++) {
            Element fileElement = (Element) fileNodeList.item(i)
            SourceFile resourceFile = new SourceFile()
            resourceFile.name = fileElement.getAttribute("name")
            resourceFile.path = fileElement.getAttribute("path")
            resourceFile.lastModified = fileElement.getAttribute("lastModified").toLong()
            lastModifiedSourceFileMap.put(resourceFile.path, resourceFile)
        }
        return lastModifiedSourceFileMap
    }

    static saveCurrentModifiedManifest(File manifestFile, Map<String, SourceFile> currentModifiedSourceMap) {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document documentTemp = builderFactory.newDocumentBuilder().newDocument()
        // resources
        Element sourceElement = documentTemp.createElement("source")
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