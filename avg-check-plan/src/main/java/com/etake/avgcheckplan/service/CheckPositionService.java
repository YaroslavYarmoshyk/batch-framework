package com.etake.avgcheckplan.service;

import com.etake.avgcheckplan.model.AvgCheckPosition;
import com.etake.avgcheckplan.model.CheckPosition;
import com.etake.avgcheckplan.repository.CheckPositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;

@Service
@RequiredArgsConstructor
public class CheckPositionService {
    private final CheckPositionRepository checkPositionRepository;

    public List<AvgCheckPosition> getAvgCheckPositionsInPeriod(final LocalDate fromDate, final LocalDate toDate) {
        return getAvgCheckPositions(checkPositionRepository.findAllInPeriod(fromDate, toDate));
    }

    public List<AvgCheckPosition> getAvgCheckPositionsInCurrentPeriod() {
        return getAvgCheckPositions(checkPositionRepository.findAllInCurrentPeriod());
    }

    private static List<AvgCheckPosition> getAvgCheckPositions(final List<CheckPosition> checkPositions) {
        return new ArrayList<>(
                checkPositions.stream()
                        .filter(checkPosition -> !checkPosition.refunded())
                        .collect(groupingBy(
                                CheckPosition::storeName,
                                collectingAndThen(
                                        Collectors.toList(),
                                        list -> {
                                            final String store = list.getFirst().storeName();
                                            final String region = list.getFirst().regionName();
                                            final BigDecimal totalAmount = list.stream()
                                                    .map(CheckPosition::amount)
                                                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                                            int count = list.size();
                                            BigDecimal avg = count == 0 ? BigDecimal.ZERO :
                                                    totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                                            return new AvgCheckPosition(region, store, avg);
                                        }
                                )
                        ))
                        .values()
        );
    }
}
