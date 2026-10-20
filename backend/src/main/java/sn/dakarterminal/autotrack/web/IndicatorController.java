package sn.dakarterminal.autotrack.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import sn.dakarterminal.autotrack.service.IndicatorService;
import sn.dakarterminal.autotrack.web.dto.Dtos.Indicators;

/** Indicateurs du pilote (superviseur uniquement, cf. SecurityConfig). */
@RestController
public class IndicatorController {

    private final IndicatorService indicators;

    public IndicatorController(IndicatorService indicators) {
        this.indicators = indicators;
    }

    @GetMapping("/api/indicators")
    public Indicators get() {
        return indicators.indicators();
    }
}
