package com.etake.cyclicaction.service.impl;

import com.etake.cyclicaction.enumeration.Algorithm;
import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.service.AlgorithmService;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@Service
public class AlgorithmServiceImpl implements AlgorithmService {
    @Override
    public Pair<Algorithm, List<Position>> definePositionsByAlgorithm(final Position position, final List<Position> history) {
        final String positionThirdGroup = position.getThirdGroup();
        final String positionStore = position.getStore();
        final Integer positionStoreFormat = position.getStoreFormat();
        final Integer positionCode = position.getActionCode();

        final List<Position> firstAlgorithmPositions = history.stream()
                .filter(firstAlgorithmCondition(positionStore, positionCode))
                .toList();
        if (!firstAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.FIRST, firstAlgorithmPositions);
        }

        final List<Position> secondAlgorithmPositions = history.stream()
                .filter(secondAlgorithmCondition(positionStoreFormat, positionCode))
                .toList();
        if (!secondAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.SECOND, secondAlgorithmPositions);
        }

        final List<Position> thirdAlgorithmPositions = history.stream()
                .filter(thirdAlgorithmCondition(positionCode))
                .toList();
        if (!thirdAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.THIRD, thirdAlgorithmPositions);
        }

        final List<Position> fourthAlgorithmPositions = history.stream()
                .filter(fourthAlgorithmCondition(positionStore, positionThirdGroup))
                .toList();
        if (!fourthAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.FOURTH, fourthAlgorithmPositions);
        }

        final List<Position> fifthAlgorithmPositions = history.stream()
                .filter(fifthAlgorithmCondition(positionStoreFormat, positionThirdGroup))
                .toList();
        if (!fifthAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.FIFTH, fifthAlgorithmPositions);
        }

        final List<Position> sixthAlgorithmPositions = history.stream()
                .filter(sixthAlgorithmCondition(positionThirdGroup))
                .toList();
        if (!sixthAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.SIXTH, sixthAlgorithmPositions);
        }

        return new Pair<>(Algorithm.ZERO, List.of());
    }

    private static Predicate<Position> firstAlgorithmCondition(final String positionStore,
                                                               final Integer positionCode) {
        return historyPos -> Objects.equals(historyPos.getStore(), positionStore)
                && Objects.equals(historyPos.getActionCode(), positionCode);
    }

    private static Predicate<Position> secondAlgorithmCondition(final Integer positionStoreFormat,
                                                                final Integer positionCode) {
        return historyPos -> Objects.equals(historyPos.getStoreFormat(), positionStoreFormat)
                && Objects.equals(historyPos.getActionCode(), positionCode);
    }

    private static Predicate<Position> thirdAlgorithmCondition(final Integer positionCode) {
        return historyPos -> Objects.equals(historyPos.getActionCode(), positionCode);
    }

    private static Predicate<Position> fourthAlgorithmCondition(final String positionStore,
                                                                final String positionThirdGroup) {
        return historyPos -> Objects.equals(historyPos.getStore(), positionStore)
                && Objects.equals(historyPos.getThirdGroup(), positionThirdGroup);
    }

    private static Predicate<Position> fifthAlgorithmCondition(final Integer positionStoreFormat,
                                                               final String positionThirdGroup) {
        return historyPos -> Objects.equals(historyPos.getStoreFormat(), positionStoreFormat)
                && Objects.equals(historyPos.getThirdGroup(), positionThirdGroup);
    }

    private static Predicate<Position> sixthAlgorithmCondition(final String positionThirdGroup) {
        return historyPos -> Objects.equals(historyPos.getThirdGroup(), positionThirdGroup);
    }
}

