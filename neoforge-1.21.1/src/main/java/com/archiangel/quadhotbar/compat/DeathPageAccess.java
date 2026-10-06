package com.archiangel.quadhotbar.compat;

/** Optional death inventories retain their own canonical items; this stores only slot addresses. */
public interface DeathPageAccess {
    int[] quadhotbar$pageSlots();

    void quadhotbar$setPageSlots(int[] slots);

    int quadhotbar$activePage();

    void quadhotbar$setActivePage(int page);
}
