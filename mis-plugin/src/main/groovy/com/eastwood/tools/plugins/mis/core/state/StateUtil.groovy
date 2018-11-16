package com.eastwood.tools.plugins.mis.core.state

import com.android.build.gradle.BaseExtension
import com.eastwood.tools.plugins.mis.core.MisUtil
import com.eastwood.tools.plugins.mis.core.extension.MisSource
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

class StateUtil {

    static void updateSourceFileState(Project project, MisSource misSource) {
        Map<String, MisFile> currentModifiedSourceFileMap = new HashMap<>()
        BaseExtension android = project.extensions.getByName('android')
        def sourceSets = android.sourceSets.getByName(misSource.flavorName)
        sourceSets.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (misSource.microModuleName != null) {
                if (it.absolutePath.endsWith(misSource.microModuleName + "${File.separator}src${File.separator + misSource.flavorName + File.separator}mis")) {
                    filterMisSourceFileState(it, currentModifiedSourceFileMap)
                }
            } else {
                filterMisSourceFileState(it, currentModifiedSourceFileMap)
            }
        }

        def typeDir = MisUtil.getTypeDir(project, misSource)
        def lastModifiedManifest = new File(typeDir, "lastModifiedManifest.xml")
        saveCurrentModifiedManifest(lastModifiedManifest, misSource.version, currentModifiedSourceFileMap)
    }

    static boolean hasModifiedSourceFile(Project project, MisSource misSource) {
        def typeDir = MisUtil.getTypeDir(project, misSource)
        def lastModifiedManifest = new File(typeDir, "lastModifiedManifest.xml")
        if (!lastModifiedManifest.exists()) {
            return true
        }
        MisSourceState sourceState = getLastModifiedSourceState(lastModifiedManifest)

        BaseExtension android = project.extensions.getByName('android')
        def sourceSets = android.sourceSets.getByName(misSource.flavorName)
        for (File it : sourceSets.aidl.srcDirs) {
            if (!it.absolutePath.endsWith("mis")) continue

            boolean result = false
            if (misSource.microModuleName != null) {
                if (it.absolutePath.endsWith(misSource.microModuleName + "${File.separator}src${File.separator + misSource.flavorName + File.separator}mis")) {
                    result = findModifiedSource(it, sourceState.lastModifiedSourceFile)
                }
            } else {
                result = findModifiedSource(it, sourceState.lastModifiedSourceFile)
            }
            if (result) {
                return true
            }
        }
        return sourceState.lastModifiedSourceFile.size() > 0
    }

    private static void filterMisSourceFileState(File file, Map<String, MisFile> sourceFileMap) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterMisSourceFileState(childFile, sourceFileMap)
            }
        } else {
            if (file.name.endsWith(".java")) {
                MisFile sourceFile = new MisFile()
                sourceFile.name = file.name
                sourceFile.path = file.absolutePath
                sourceFile.lastModified = file.lastModified()
                sourceFileMap.put(sourceFile.path, sourceFile)
            }
        }
    }

    private static saveCurrentModifiedManifest(File manifestFile, String version, Map<String, MisFile> currentModifiedSourceMap) {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document documentTemp = builderFactory.newDocumentBuilder().newDocument()
        // resources
        Element sourceElement = documentTemp.createElement("source")
        sourceElement.setAttribute("version", version)
        currentModifiedSourceMap.each {
            MisFile sourceFile = it.value
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

    private static MisSourceState getLastModifiedSourceState(File lastModifiedManifestFile) {
        MisSourceState source = new MisSourceState()
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
            MisFile resourceFile = new MisFile()
            resourceFile.name = fileElement.getAttribute("name")
            resourceFile.path = fileElement.getAttribute("path")
            resourceFile.lastModified = fileElement.getAttribute("lastModified").toLong()
            source.lastModifiedSourceFile.put(resourceFile.path, resourceFile)
        }
        return source
    }

    private static boolean findModifiedSource(File file, Map<String, MisFile> lastModifiedSourceFileMap) {
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
                MisFile sourceFile = lastModifiedSourceFileMap.get(file.absolutePath)
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
