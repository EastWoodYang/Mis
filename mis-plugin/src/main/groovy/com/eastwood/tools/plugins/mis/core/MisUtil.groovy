package com.eastwood.tools.plugins.mis.core

import com.android.build.gradle.BaseExtension
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

class MisUtil {

    static MisSource getMisSourceFormManifest(Project project, String groupId, String artifactId) {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        File misDir = new File(project.rootDir, '.gradle/mis')
        if (!misDir.exists()) {
            return null
        }

        File publicationManifest = new File(misDir, 'publicationManifest.xml')
        if (!publicationManifest.exists()) {
            return null
        }

        Document document = builderFactory.newDocumentBuilder().parse(publicationManifest)
        NodeList publicationNodeList = document.documentElement.getElementsByTagName("publication")
        for (int i = 0; i < publicationNodeList.getLength(); i++) {
            Element publicationElement = (Element) publicationNodeList.item(i)
            def groupIdTemp = publicationElement.getAttribute("groupId")
            def artifactIdTemp = publicationElement.getAttribute("artifactId")
            if (groupId == groupIdTemp && artifactId == artifactIdTemp) {
                MisSource misSource = new MisSource()
                misSource.groupId = groupId
                misSource.artifactId = artifactId
                misSource.version = publicationElement.getAttribute("version")
                misSource.invalid = Boolean.valueOf(publicationElement.getAttribute("invalid"))
                return misSource
            }
        }
        return null
    }

    static updateMisSourceManifest(Project project, List<MisSource> misSourceList) {
        Map<String, MisSource> misSourceMap = new HashMap<>()
        misSourceList.each {
            misSourceMap.put(it.groupId + ":" + it.artifactId, it)
        }

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        File misDir = new File(project.rootDir, '.gradle/mis')
        if (!misDir.exists()) {
            misDir.mkdirs()
        }

        Document document
        Element misSourceElement

        File publicationManifest = new File(misDir, 'publicationManifest.xml')
        if (publicationManifest.exists()) {
            document = builderFactory.newDocumentBuilder().parse(publicationManifest)
            NodeList misSourceNodeList = document.getElementsByTagName("manifest")
            if (misSourceNodeList.length == 0) {
                misSourceElement = document.createElement("manifest")
            } else {
                misSourceElement = (Element) misSourceNodeList.item(0)
                NodeList publicationNodeList = misSourceElement.getElementsByTagName("publication")
                for (int i = 0; i < publicationNodeList.getLength(); i++) {
                    Element publicationElement = (Element) publicationNodeList.item(i)
                    def groupId = publicationElement.getAttribute("groupId")
                    def artifactId = publicationElement.getAttribute("artifactId")

                    def key = groupId + ":" + artifactId
                    def misSource = misSourceMap.get(key)
                    if (misSource != null) {
                        misSourceMap.remove(key)

                        publicationElement.setAttribute('groupId', misSource.groupId)
                        publicationElement.setAttribute('artifactId', misSource.artifactId)
                        publicationElement.setAttribute('version', misSource.version)
                        publicationElement.setAttribute('invalid', misSource.invalid ? "true" : "false")

                        while (publicationElement.hasChildNodes())
                            publicationElement.removeChild(publicationElement.getFirstChild());

                        misSource.paths.each {
                            Element sourceSetElement = document.createElement('sourceSet')
                            sourceSetElement.setAttribute('path', it)
                            publicationElement.appendChild(sourceSetElement)
                        }

                    }
                }
            }
        } else {
            document = builderFactory.newDocumentBuilder().newDocument()
            misSourceElement = document.createElement("manifest")
        }

        misSourceMap.each {
            MisSource misSource = it.value
            Element publicationElement = document.createElement('publication')
            publicationElement.setAttribute('groupId', misSource.groupId)
            publicationElement.setAttribute('artifactId', misSource.artifactId)
            publicationElement.setAttribute('version', misSource.version)
            publicationElement.setAttribute('invalid', misSource.invalid ? "true" : "false")

            misSource.paths.each {
                Element sourceSetElement = document.createElement('sourceSet')
                sourceSetElement.setAttribute('path', it)
                publicationElement.appendChild(sourceSetElement)
            }
            misSourceElement.appendChild(publicationElement)
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        transformer.transform(new DOMSource(misSourceElement), new StreamResult(publicationManifest))
    }

    static setProjectMisSourceDirs(Project project) {
        def type = "main"
        BaseExtension android = project.extensions.getByName('android')
        def obj = android.sourceSets.getByName(type)

        obj.java.srcDirs.each {
            obj.aidl.srcDirs(it.absolutePath.replace('java', 'mis'))
        }
    }

    static boolean isMicroModule(Project project) {
        return project.plugins.findPlugin("micro-module")
    }

    static boolean hasAndroidPlugin(Project project) {
        if (project.plugins.findPlugin("com.android.application") || project.plugins.findPlugin("android") ||
                project.plugins.findPlugin("com.android.test")) {
            return true
        } else if (project.plugins.findPlugin("com.android.library") || project.plugins.findPlugin("android-library")) {
            return true
        } else {
            return false
        }
    }

    static Map<String, ?> optionsFilter(MisSource misSource) {
        Map<String, ?> opts = new HashMap<>()
        opts.put("group", misSource.groupId)
        opts.put("name", misSource.artifactId)
        opts.put("version", misSource.version)
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

    static File getTypeDir(Project project, MisSource misSource) {
        def misRoot = new File(project.projectDir, 'build/mis')
        def dirPath
        if(isMicroModule(project)) {
            dirPath = misSource.name.replace(":", "/")
        } else {
            dirPath = misSource.name
        }
        return new File(misRoot, dirPath)
    }

}