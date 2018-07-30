package com.eastwood.demo.library;

import com.eastwood.common.autoinject.AutoBowArrow;
import com.eastwood.common.autoinject.IAutoBowArrow;
import com.eastwood.common.mis.MisService;

@AutoBowArrow(target = "register")
public class MisAutoArrowBow implements IAutoBowArrow {

    @Override
    public void shoot() {
        MisService.register(ILibraryService.class, LibraryService.class);
        // or
        // MisService.register(ILibraryService.class, new LibraryService());
    }

}
