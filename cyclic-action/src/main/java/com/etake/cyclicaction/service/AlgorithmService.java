package com.etake.cyclicaction.service;

import com.etake.cyclicaction.enumeration.Algorithm;
import com.etake.cyclicaction.model.Position;
import org.apache.commons.math3.util.Pair;

import java.util.List;

public interface AlgorithmService {

    Pair<Algorithm, List<Position>> definePositionsByAlgorithm(final Position position, final List<Position> history);
}
