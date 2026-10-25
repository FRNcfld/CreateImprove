package com.molox.createimp.compat.simulated;

import java.util.Map;

/** Per-block-entity text bindings, separate from Simulated's Create item-frequency entries. */
public interface TypewriterTextAccess {
    Map<Integer, String> createimp$getTextBindings();

    void createimp$replaceTextBindings(Map<Integer, String> bindings);

    void createimp$pressTextKey(int key);

    void createimp$releaseTextKey(int key);

    void createimp$stopAllTextKeys();
}
