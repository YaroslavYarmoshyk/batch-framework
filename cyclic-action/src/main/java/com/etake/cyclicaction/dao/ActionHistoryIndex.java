package com.etake.cyclicaction.dao;

import com.etake.cyclicaction.model.Position;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ActionHistoryIndex {
    private final Map<StoreActionCodeKey, List<Position>> byStoreAndActionCode;
    private final Map<StoreFormatActionCodeKey, List<Position>> byStoreFormatAndActionCode;
    private final Map<Integer, List<Position>> byActionCode;
    private final Map<StoreThirdGroupKey, List<Position>> byStoreAndThirdGroup;
    private final Map<StoreFormatThirdGroupKey, List<Position>> byStoreFormatAndThirdGroup;
    private final Map<String, List<Position>> byThirdGroup;

    private ActionHistoryIndex(final Map<StoreActionCodeKey, List<Position>> byStoreAndActionCode,
                               final Map<StoreFormatActionCodeKey, List<Position>> byStoreFormatAndActionCode,
                               final Map<Integer, List<Position>> byActionCode,
                               final Map<StoreThirdGroupKey, List<Position>> byStoreAndThirdGroup,
                               final Map<StoreFormatThirdGroupKey, List<Position>> byStoreFormatAndThirdGroup,
                               final Map<String, List<Position>> byThirdGroup) {
        this.byStoreAndActionCode = byStoreAndActionCode;
        this.byStoreFormatAndActionCode = byStoreFormatAndActionCode;
        this.byActionCode = byActionCode;
        this.byStoreAndThirdGroup = byStoreAndThirdGroup;
        this.byStoreFormatAndThirdGroup = byStoreFormatAndThirdGroup;
        this.byThirdGroup = byThirdGroup;
    }

    public static ActionHistoryIndex build(final List<Position> history) {
        final Map<StoreActionCodeKey, List<Position>> byStoreAndActionCode = new HashMap<>();
        final Map<StoreFormatActionCodeKey, List<Position>> byStoreFormatAndActionCode = new HashMap<>();
        final Map<Integer, List<Position>> byActionCode = new HashMap<>();
        final Map<StoreThirdGroupKey, List<Position>> byStoreAndThirdGroup = new HashMap<>();
        final Map<StoreFormatThirdGroupKey, List<Position>> byStoreFormatAndThirdGroup = new HashMap<>();
        final Map<String, List<Position>> byThirdGroup = new HashMap<>();

        for (final Position position : history) {
            byStoreAndActionCode.computeIfAbsent(
                    new StoreActionCodeKey(position.getStore(), position.getActionCode()), _ -> new ArrayList<>()).add(position);
            byStoreFormatAndActionCode.computeIfAbsent(
                    new StoreFormatActionCodeKey(position.getStoreFormat(), position.getActionCode()), _ -> new ArrayList<>()).add(position);
            byActionCode.computeIfAbsent(position.getActionCode(), _ -> new ArrayList<>()).add(position);
            byStoreAndThirdGroup.computeIfAbsent(
                    new StoreThirdGroupKey(position.getStore(), position.getThirdGroup()), _ -> new ArrayList<>()).add(position);
            byStoreFormatAndThirdGroup.computeIfAbsent(
                    new StoreFormatThirdGroupKey(position.getStoreFormat(), position.getThirdGroup()), _ -> new ArrayList<>()).add(position);
            byThirdGroup.computeIfAbsent(position.getThirdGroup(), _ -> new ArrayList<>()).add(position);
        }

        return new ActionHistoryIndex(byStoreAndActionCode, byStoreFormatAndActionCode, byActionCode,
                byStoreAndThirdGroup, byStoreFormatAndThirdGroup, byThirdGroup);
    }

    public List<Position> byStoreAndActionCode(final String store, final Integer actionCode) {
        return byStoreAndActionCode.getOrDefault(new StoreActionCodeKey(store, actionCode), List.of());
    }

    public List<Position> byStoreFormatAndActionCode(final Integer storeFormat, final Integer actionCode) {
        return byStoreFormatAndActionCode.getOrDefault(new StoreFormatActionCodeKey(storeFormat, actionCode), List.of());
    }

    public List<Position> byActionCode(final Integer actionCode) {
        return byActionCode.getOrDefault(actionCode, List.of());
    }

    public List<Position> byStoreAndThirdGroup(final String store, final String thirdGroup) {
        return byStoreAndThirdGroup.getOrDefault(new StoreThirdGroupKey(store, thirdGroup), List.of());
    }

    public List<Position> byStoreFormatAndThirdGroup(final Integer storeFormat, final String thirdGroup) {
        return byStoreFormatAndThirdGroup.getOrDefault(new StoreFormatThirdGroupKey(storeFormat, thirdGroup), List.of());
    }

    public List<Position> byThirdGroup(final String thirdGroup) {
        return byThirdGroup.getOrDefault(thirdGroup, List.of());
    }

    private record StoreFormatActionCodeKey(Integer storeFormat, Integer actionCode) {
    }

    private record StoreThirdGroupKey(String store, String thirdGroup) {
    }

    private record StoreFormatThirdGroupKey(Integer storeFormat, String thirdGroup) {
    }
}
