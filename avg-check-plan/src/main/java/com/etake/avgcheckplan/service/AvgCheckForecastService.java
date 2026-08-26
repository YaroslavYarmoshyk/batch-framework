package com.etake.avgcheckplan.service;

import java.util.List;

public interface AvgCheckForecastService<T> {

    List<T> getForecastPositions();
}
