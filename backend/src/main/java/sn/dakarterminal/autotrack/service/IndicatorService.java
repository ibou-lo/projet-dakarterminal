package sn.dakarterminal.autotrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.PilotMeasure;
import sn.dakarterminal.autotrack.repository.PilotMeasureRepository;
import sn.dakarterminal.autotrack.web.dto.Dtos.IndicatorSeries;
import sn.dakarterminal.autotrack.web.dto.Dtos.Indicators;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Les quatre indicateurs du pilote. La semaine 0 est la référence mesurée avant
 * le démarrage ; les semaines suivantes sont saisies ou calculées pendant le pilote.
 * Valeurs de démonstration fictives.
 */
@Service
@Transactional(readOnly = true)
public class IndicatorService {

    private record Def(String code, String title, String unit, boolean lowerIsBetter, String method) {
    }

    private static final List<Def> DEFS = List.of(
            new Def("SEARCH_TIME_MIN", "Temps moyen pour retrouver un véhicule", "min", true,
                    "Chronométrage d'un échantillon de recherches par semaine"),
            new Def("INVENTORY_RELIABILITY_PCT", "Fiabilité de l'inventaire physique", "%", false,
                    "Inventaire tournant : places scannées vs positions enregistrées"),
            new Def("PICKUP_PREP_MIN", "Temps de préparation d'un enlèvement", "min", true,
                    "De la demande client à la présentation au portail"),
            new Def("TRACE_COMPLETE_PCT", "Mouvements avec trace complète", "%", false,
                    "Départ et arrivée scannés, avec agent et heure"));

    private final PilotMeasureRepository measures;

    public IndicatorService(PilotMeasureRepository measures) {
        this.measures = measures;
    }

    public Indicators indicators() {
        List<IndicatorSeries> out = new ArrayList<>();
        for (Def d : DEFS) {
            List<PilotMeasure> all = measures.findByIndicatorOrderByWeekAsc(d.code());
            double ref = all.stream().filter(m -> m.getWeek() == 0).mapToDouble(PilotMeasure::getMeasuredValue).findFirst().orElse(0);
            List<Double> weeks = all.stream().filter(m -> m.getWeek() > 0).map(PilotMeasure::getMeasuredValue).toList();
            double current = weeks.isEmpty() ? ref : weeks.get(weeks.size() - 1);
            String delta;
            if ("%".equals(d.unit())) {
                delta = String.format(Locale.FRANCE, "%+.1f pts", current - ref);
            } else {
                delta = ref == 0 ? "—" : String.format(Locale.FRANCE, "%+.0f %%", (current - ref) * 100 / ref);
            }
            out.add(new IndicatorSeries(d.code(), d.title(), d.unit(), ref, current, delta, d.lowerIsBetter(), weeks, d.method()));
        }
        return new Indicators(true, out);
    }
}
