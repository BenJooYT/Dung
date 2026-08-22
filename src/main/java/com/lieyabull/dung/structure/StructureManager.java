package com.lieyabull.dung.structure;

import com.lieyabull.dung.Dung;

/**
 * Top-level coordinator for the WorldEdit structure library (replaces the old custom room-editor
 * coordinator). Owns the {@link StructureRegistry} and exposes reload, so admins can add hand-authored
 * {@code structure.schem + structure.yml} files to {@code plugins/Dung/structures/} and pick them up
 * without a full restart.
 */
public final class StructureManager {
    private final Dung plugin;
    private final StructureRegistry registry;

    public StructureManager(Dung plugin) {
        this.plugin = plugin;
        this.registry = new StructureRegistry(plugin);
    }

    public StructureRegistry registry() {
        return registry;
    }

    /** Reload the structure library from disk (re-scans the structures directory). */
    public int reload() {
        registry.load();
        return registry.size();
    }
}