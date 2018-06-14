package com.eastwood.demo.library;

/**
 * @author eastwood
 * createDate: 2018-06-14
 */
public class LibraryService implements ILibraryService {

    @Override
    public String getServiceName() {
        return "LibraryService";
    }

    @Override
    public LibraryInfo getLibraryInfo() {
        LibraryInfo libraryInfo = new LibraryInfo();
        libraryInfo.setName("library");
        return libraryInfo;
    }

}
