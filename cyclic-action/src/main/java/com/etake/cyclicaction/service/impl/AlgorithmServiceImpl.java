package com.etake.cyclicaction.service.impl;

import com.etake.cyclicaction.dao.ActionHistoryIndex;
import com.etake.cyclicaction.enumeration.Algorithm;
import com.etake.cyclicaction.model.Position;
import com.etake.cyclicaction.service.AlgorithmService;
import org.apache.commons.math3.util.Pair;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlgorithmServiceImpl implements AlgorithmService {
    @Override
    public Pair<Algorithm, List<Position>> definePositionsByAlgorithm(final Position position, final ActionHistoryIndex historyIndex) {
        final String positionThirdGroup = position.getThirdGroup();
        final String positionStore = position.getStore();
        final Integer positionStoreFormat = position.getStoreFormat();
        final Integer positionCode = position.getActionCode();

        final List<Position> firstAlgorithmPositions = historyIndex.byStoreAndActionCode(positionStore, positionCode);
        if (!firstAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.FIRST, firstAlgorithmPositions);
        }

        final List<Position> secondAlgorithmPositions = historyIndex.byStoreFormatAndActionCode(positionStoreFormat, positionCode);
        if (!secondAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.SECOND, secondAlgorithmPositions);
        }

        final List<Position> thirdAlgorithmPositions = historyIndex.byActionCode(positionCode);
        if (!thirdAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.THIRD, thirdAlgorithmPositions);
        }

        final List<Position> fourthAlgorithmPositions = historyIndex.byStoreAndThirdGroup(positionStore, positionThirdGroup);
        if (!fourthAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.FOURTH, fourthAlgorithmPositions);
        }

        final List<Position> fifthAlgorithmPositions = historyIndex.byStoreFormatAndThirdGroup(positionStoreFormat, positionThirdGroup);
        if (!fifthAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.FIFTH, fifthAlgorithmPositions);
        }

        final List<Position> sixthAlgorithmPositions = historyIndex.byThirdGroup(positionThirdGroup);
        if (!sixthAlgorithmPositions.isEmpty()) {
            return new Pair<>(Algorithm.SIXTH, sixthAlgorithmPositions);
        }

        return new Pair<>(Algorithm.ZERO, List.of());
    }
}
