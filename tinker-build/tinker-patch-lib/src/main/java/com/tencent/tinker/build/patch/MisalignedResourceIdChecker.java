/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.tencent.tinker.build.patch;

import com.tencent.tinker.build.util.TinkerPatchException;
import com.tencent.tinker.commons.util.IOHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import tinker.net.dongliu.apk.parser.ApkParser;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceEntry;
import tinker.net.dongliu.apk.parser.struct.resource.ResourcePackage;
import tinker.net.dongliu.apk.parser.struct.resource.ResourceTable;
import tinker.net.dongliu.apk.parser.struct.resource.Type;

/**
 * Before building a patch package, verifies that resource ids are not misaligned between APKs:
 * each resource key from the old APK must keep the same id (0xPPTTEEEE) in the new APK when that
 * key still exists there (rebuild without stable id mapping often shifts ids for existing names).
 *
 * <p>Keys are {@code packageName/resTypeName/entryName}; ids are synthesized from resources.arsc
 * using package id, type id, and entry index.
 */
public final class MisalignedResourceIdChecker {

    private MisalignedResourceIdChecker() {}

    /**
     * Runs the full check and throws only after scanning all keys, if any problem was found.
     *
     * @throws IOException          if an APK cannot be read
     * @throws TinkerPatchException if one or more resource id problems were found (message lists all)
     */
    public static void check(File oldApk, File newApk) throws IOException {
        final List<String> violations = new ArrayList<>();
        final Map<String, Integer> oldIds = buildResourceKeyToIdMap(oldApk, violations);
        final Map<String, Integer> newIds = buildResourceKeyToIdMap(newApk, violations);

        for (Map.Entry<String, Integer> e : oldIds.entrySet()) {
            final String key = e.getKey();
            final Integer oldId = e.getValue();
            final Integer newId = newIds.get(key);
            if (newId != null && !Objects.equals(oldId, newId)) {
                violations.add(String.format(
                    "id mismatch for existing resource [%s]: old=0x%08x new=0x%08x",
                    key, oldId, newId));
            }
        }

        if (!violations.isEmpty()) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Misaligned resource id check failed (").append(violations.size()).append(" issue(s)):");
            for (String line : violations) {
                sb.append('\n').append("  - ").append(line);
            }
            sb.append("\npatch generation aborted. Check if the resource mapping file is valid or applied correctly.");
            throw new TinkerPatchException(sb.toString());
        }
    }

    private static Map<String, Integer> buildResourceKeyToIdMap(File apk, List<String> violations) throws IOException {
        ApkParser parser = null;
        try {
            parser = new ApkParser(apk);
            parser.parseResourceTable();
            final ResourceTable table = parser.getResourceTable();
            if (table == null) {
                violations.add("missing resource table in " + apk.getAbsolutePath());
                return new HashMap<>();
            }
            final Map<String, Integer> map = new HashMap<>();
            final Map<String, ResourcePackage> pkgNameMap = table.getPackageNameMap();
            if (pkgNameMap == null) {
                return map;
            }
            final String apkLabel = apk.getAbsolutePath();
            for (ResourcePackage pkg : pkgNameMap.values()) {
                final String packageName = pkg.getName();
                final short pkgId = pkg.getId();
                final Map<String, List<Type>> typesByName = pkg.getTypesNameMap();
                if (typesByName == null) {
                    continue;
                }
                for (Map.Entry<String, List<Type>> typeListEntry : typesByName.entrySet()) {
                    final String resTypeName = typeListEntry.getKey();
                    final List<Type> types = typeListEntry.getValue();
                    if (types == null) {
                        continue;
                    }
                    for (Type type : types) {
                        type.parseAllResourceEntry();
                        final long[] offsets = type.getOffsets();
                        if (offsets == null) {
                            continue;
                        }
                        final short typeId = type.getId();
                        for (int i = 0; i < offsets.length; i++) {
                            final ResourceEntry entry = type.getResourceEntry(i);
                            if (entry == null) {
                                continue;
                            }
                            final String entryKey = entry.getKey();
                            if (entryKey == null) {
                                continue;
                            }
                            final int resourceId =
                                ((pkgId & 0xff) << 24) | ((typeId & 0xff) << 16) | (i & 0xffff);
                            String fullKey = packageName + "/" + resTypeName + "/" + entryKey;
                            putConsistent(map, fullKey, resourceId, apkLabel, violations);
                        }
                    }
                }
            }
            return map;
        } finally {
            IOHelper.closeQuietly(parser);
        }
    }

    /**
     * Keeps the first id for a key; further conflicting ids for the same key are recorded in {@code violations}.
     */
    private static void putConsistent(
        Map<String, Integer> map,
        String key,
        int resourceId,
        String apkPath,
        List<String> violations
    ) {
        if (map.containsKey(key)) {
            Integer existing = map.get(key);
            if (!existing.equals(resourceId)) {
                violations.add(String.format(
                    "inconsistent resource id for [%s] inside apk %s: 0x%08x vs 0x%08x",
                    key, apkPath, existing, resourceId));
            }
            return;
        }
        map.put(key, resourceId);
    }
}
