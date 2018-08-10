package com.eastwood.tools.plugins.mis

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult;

class SourceStateUtil {

    static void updateSourceFileState(Project project, Map<String, ?> options) {
        Map<String, SourceFile> currentModifiedSourceFileMap = new HashMap<>()
        boolean isMicroModule = Util.isMicroModule(project)
        BaseExtension android = project.extensions.getByName('android')
        def main = android.sourceSets.getByName('main')
        main.aidl.srcDirs.each {
            if (!it.absolutePath.endsWith("mis")) return

            if (isMicroModule) {
                if (options.microModuleName == null) {
                    filterMisSourceFileState(it, currentModifiedSourceFileMap)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
                    filterMisSourceFileState(it, currentModifiedSourceFileMap)
                }
            } else {
                filterMisSourceFileState(it, currentModifiedSourceFileMap)
            }
        }

        def typeDir = Util.getTypeDir(project, options)
        def lastModifiedManifest = new File(typeDir, "lastModifiedManifest.xml")
        saveCurrentModifiedManifest(lastModifiedManifest, options.version, currentModifiedSourceFileMap)
    }

    static boolean hasModifiedSourceFile(Project project, Map<String, ?> options) {
        def typeDir = Util.getTypeDir(project, options)
        def lastModifiedManifest = new File(typeDir, "lastModifiedManifest.xml")
        if (!lastModifiedManifest.exists()) {
            return true
        }
        SourceState sourceState = getLastModifiedSourceState(lastModifiedManifest)

        boolean isMicroModule = Util.isMicroModule(project)
        BaseExtension android = project.extensions.getByName('android')
        def main = android.sourceSets.getByName('main')
        for (File it : main.aidl.srcDirs) {
            if (!it.absolutePath.endsWith("mis")) continue

            boolean result = false
            if (isMicroModule) {
                if (options.microModuleName == null) {
                    result = findModifiedSource(it, sourceState.lastModifiedSourceFile)
                } else if (options.microModuleName != null && it.absolutePath.endsWith(options.microModuleName + "${File.separator}src${File.separator}main${File.separator}mis")) {
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

    private static void filterMisSourceFileState(File file, Map<String, SourceFile> sourceFileMap) {
        if (file.isDirectory()) {
            file.listFiles().each { File childFile ->
                filterMisSourceFileState(childFile, sourceFileMap)
            }
        } else {
            if (file.name.endsWith(".java")) {
                SourceFile sourceFile = new SourceFile()
                sourceFile.name = file.name
                sourceFile.path = file.absolutePath
                sourceFile.lastModified = file.lastModified()
                sourceFileMap.put(sourceFile.path, sourceFile)
            }
        }
    }

    private static saveCurrentModifiedManifest(File manifestFile, String version, Map<String, SourceFile> currentModifiedSourceMap) {
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

    private static SourceState getLastModifiedSourceState(File lastModifiedManifestFile) {
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
