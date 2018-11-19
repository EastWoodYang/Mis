package com.eastwood.tools.plugins.mis.core

import com.eastwood.tools.plugins.mis.core.extension.Publication
import org.gradle.api.GradleException
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

class PublicationManager {

    private static PublicationManager sPublicationManager

    boolean hasLoadManifest

    private File misDir
    private Map<String, Publication> publicationMap
    private boolean hasModified

    static getInstance() {
        if (sPublicationManager == null) {
            sPublicationManager = new PublicationManager()
        }
        return sPublicationManager
    }

    boolean hasLoadManifest() {
        return hasLoadManifest
    }

    void loadManifest(Project rootProject) {
        hasLoadManifest = true
        hasModified = false
        publicationMap = new HashMap<>()

        rootProject.gradle.buildFinished {
            hasLoadManifest = false
            if (it.failure != null) {
                return
            }

            if(!hasModified) {
                publicationMap.values().each {
                    if(!it.hit) {
                        hasModified = true
                    }
                }
            }

            if(hasModified) {
                hasModified = false
                saveManifest()
            }
        }

        misDir = new File(rootProject.rootDir, '.gradle/mis')
        if (!misDir.exists()) {
            return
        }

        File publicationManifest = new File(misDir, 'publicationManifest.xml')
        if (!publicationManifest.exists()) {
            return
        }

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document document = builderFactory.newDocumentBuilder().parse(publicationManifest)
        NodeList publicationNodeList = document.documentElement.getElementsByTagName("publication")
        for (int i = 0; i < publicationNodeList.getLength(); i++) {
            Element publicationElement = (Element) publicationNodeList.item(i)

            Publication publication = new Publication()
            publication.project = publicationElement.getAttribute("project")
            publication.groupId = publicationElement.getAttribute("groupId")
            publication.artifactId = publicationElement.getAttribute("artifactId")
            publication.version = publicationElement.getAttribute("version")
            publication.invalid = Boolean.valueOf(publicationElement.getAttribute("invalid"))

            publication.sourceSets = new HashMap<>()
            NodeList sourceSetNodeList = publicationElement.getElementsByTagName("sourceSet")
            for (int j = 0; j < sourceSetNodeList.getLength(); j++) {
                Element sourceSetElement = (Element) sourceSetNodeList.item(j)
                SourceSet sourceSet = new SourceSet()
                sourceSet.path = sourceSetElement.getAttribute("path")
                sourceSet.lastModifiedSourceFile = new HashMap<>()
                NodeList fileNodeList = sourceSetElement.getElementsByTagName("file")
                for (int k = 0; k < fileNodeList.getLength(); k++) {
                    Element fileElement = (Element) fileNodeList.item(k)
                    SourceFile sourceFile = new SourceFile()
                    sourceFile.path = fileElement.getAttribute("path")
                    sourceFile.lastModified = fileElement.getAttribute("lastModified").toLong()
                    sourceSet.lastModifiedSourceFile.put(sourceFile.path, sourceFile)
                }
                publication.sourceSets.put(sourceSet.path, sourceSet)
            }
            publicationMap.put(publication.groupId + ":" + publication.artifactId, publication)
        }

    }

    private void saveManifest() {
        if (!misDir.exists()) {
            misDir.mkdirs()
        }
        File publicationManifest = new File(misDir, 'publicationManifest.xml')

        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance()
        Document document = builderFactory.newDocumentBuilder().newDocument()
        Element manifestElement = document.createElement("manifest")
        publicationMap.each {
            Publication publication = it.value
            if(!publication.hit) return

            Element publicationElement = document.createElement('publication')
            publicationElement.setAttribute('project', publication.project)
            publicationElement.setAttribute('groupId', publication.groupId)
            publicationElement.setAttribute('artifactId', publication.artifactId)
            publicationElement.setAttribute('version', publication.version)
            publicationElement.setAttribute('invalid', publication.invalid ? "true" : "false")

            publication.sourceSets.each {
                SourceSet sourceSet = it.value
                Element sourceSetElement = document.createElement('sourceSet')
                sourceSetElement.setAttribute('path', sourceSet.path)
                sourceSet.lastModifiedSourceFile.each {
                    SourceFile sourceFile = it.value
                    Element sourceFileElement = document.createElement('file')
                    sourceFileElement.setAttribute('path', sourceFile.path)
                    sourceFileElement.setAttribute('lastModified', sourceFile.lastModified.toString())
                    sourceSetElement.appendChild(sourceFileElement)
                }
                publicationElement.appendChild(sourceSetElement)
            }
            manifestElement.appendChild(publicationElement)
        }

        Transformer transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.CDATA_SECTION_ELEMENTS, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        transformer.transform(new DOMSource(manifestElement), new StreamResult(publicationManifest))
    }

    boolean hasModified(Publication publication) {
        def key = publication.groupId + ":" + publication.artifactId
        Publication lashPublication = publicationMap.get(key)
        if (lashPublication == null) {
            return true
        }
        return hasModifiedSourceSet(publication.sourceSets, lashPublication.sourceSets)
    }

    private boolean hasModifiedSourceSet(Map<String, SourceSet> map1, Map<String, SourceSet> map2) {
        if (map1.size() != map2.size()) {
            return true
        }
        for (Map.Entry<String, SourceSet> entry1 : map1.entrySet()) {
            SourceSet sourceSet1 = entry1.getValue()
            SourceSet sourceSet2 = map2.get(entry1.getKey())
            if (sourceSet2 == null) {
                return true
            }
            if (hasModifiedSourceFile(sourceSet1.lastModifiedSourceFile, sourceSet2.lastModifiedSourceFile)) {
                return true
            }
        }
        return false
    }

    private boolean hasModifiedSourceFile(Map<String, SourceFile> map1, Map<String, SourceFile> map2) {
        if (map1.size() != map2.size()) {
            return true
        }
        for (Map.Entry<String, SourceFile> entry1 : map1.entrySet()) {
            SourceFile sourceFile1 = entry1.getValue()
            SourceFile sourceFile2 = map2.get(entry1.getKey())
            if (sourceFile2 == null) {
                return true
            }
            if (sourceFile1.lastModified != sourceFile2.lastModified) {
                return true
            }
        }
        return false
    }

    void addPublication(Publication publication) {
        def key = publication.groupId + ":" + publication.artifactId
        publicationMap.put(key, publication)
        hasModified = true
    }

    Publication getPublication(String groupId, String artifactId) {
        def key = groupId + ":" + artifactId
        return publicationMap.get(key)
    }

    void hitPublication(Publication publication) {
        def key = publication.groupId + ":" + publication.artifactId
        Publication oldPublication = publicationMap.get(key)
        if(oldPublication == null) return

        if(oldPublication.hit) {
            throw new GradleException("Already exists publication " + publication.groupId + ":" + publication.artifactId + " in project '${oldPublication.project}'.")
        } else {
            oldPublication.hit = true
        }
    }

}