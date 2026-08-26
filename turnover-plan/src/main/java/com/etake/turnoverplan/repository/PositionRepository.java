package com.etake.turnoverplan.repository;

import com.etake.turnoverplan.model.Position;

import java.time.LocalDate;
import java.util.List;

public interface PositionRepository {

    List<Position> findAllInPeriod(final LocalDate fromDate, final LocalDate toDate, final List<LocalDate> exclusionDates);
}
