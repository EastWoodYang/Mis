package com.eastwood.demo.library;

/**
 * @author eastwood
 * createDate: 2018-06-14
 */
public class LibraryService implements ILibraryService {

    @Override
    public LibraryModel getLibraryInfo() {
        LibraryModel libraryInfo = new LibraryModel();
        libraryInfo.setName("library");
        return libraryInfo;
    }

}
