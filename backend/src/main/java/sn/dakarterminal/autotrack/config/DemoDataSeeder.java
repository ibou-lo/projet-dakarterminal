package sn.dakarterminal.autotrack.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sn.dakarterminal.autotrack.domain.*;
import sn.dakarterminal.autotrack.domain.Enums.*;
import sn.dakarterminal.autotrack.repository.*;
import sn.dakarterminal.autotrack.security.TicketSigner;

import java.time.*;
import java.util.*;

/**
 * Données de démonstration FICTIVES, reprenant le scénario de la maquette :
 * le véhicule VF3LCYHZPJS218456 est en B · 3 · 18, autorisation de sortie en attente.
 */
@Component
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String DEMO_VIN = "VF3LCYHZPJS218456";

    private final AutoTrackProperties props;
    private final PasswordEncoder encoder;
    private final AppUserRepository users;
    private final ParkingPlaceRepository places;
    private final VehicleRepository vehicles;
    private final InspectionRepository inspections;
    private final FormalityRepository formalities;
    private final MovementRepository movements;
    private final ReadEventRepository reads;
    private final TicketRepository tickets;
    private final GatePassageRepository passages;
    private final PilotMeasureRepository measures;
    private final TicketSigner signer;
    private final KeyEventRepository keyEvents;
    private final ParkZoneRepository zones;
    private final AuditEventRepository auditEvents;

    private final Random rnd = new Random(42);
    private final Instant now = Instant.now();
    private final Map<String, ParkingPlace> placeByCode = new HashMap<>();
    private int ticketSeq = 4700;

    public DemoDataSeeder(AutoTrackProperties props, PasswordEncoder encoder, AppUserRepository users,
                          ParkingPlaceRepository places, VehicleRepository vehicles, InspectionRepository inspections,
                          FormalityRepository formalities, MovementRepository movements, ReadEventRepository reads,
                          TicketRepository tickets, GatePassageRepository passages, PilotMeasureRepository measures,
                          TicketSigner signer, KeyEventRepository keyEvents, ParkZoneRepository zones,
                          AuditEventRepository auditEvents) {
        this.props = props;
        this.encoder = encoder;
        this.users = users;
        this.places = places;
        this.vehicles = vehicles;
        this.inspections = inspections;
        this.formalities = formalities;
        this.movements = movements;
        this.reads = reads;
        this.tickets = tickets;
        this.passages = passages;
        this.measures = measures;
        this.signer = signer;
        this.keyEvents = keyEvents;
        this.zones = zones;
        this.auditEvents = auditEvents;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.demo().seed()) return;
        if (users.count() > 0) {
            ensureAdmin(); // base créée avant l'ajout du profil administrateur
            log.info("Base existante ({} véhicules) : données conservées. Pour recharger la démo : démarrer une fois avec DDL_AUTO=create",
                    vehicles.count());
            return;
        }
        seedUsers();
        seedPlaces();
        seedKeyVehicles();
        seedFillers();
        seedPilotMeasures();
        seedKeys();
        seedAudit();
        log.info("Données de démonstration chargées : {} véhicules, {} places", vehicles.count(), places.count());
    }

    // ------------------------------------------------------------------ utilisateurs

    private void seedUsers() {
        user("admin", "Admin@2026", "F. Sow", Role.ADMIN, null, "f.sow@dakar-terminal.example", 40);
        user("superviseur", "Superviseur@2026", "M. Diop", Role.SUPERVISEUR, "B", "m.diop@dakar-terminal.example", 38);
        user("agent.diallo", "Agent@2026", "A. Diallo", Role.AGENT, "B", null, 30);
        user("agent.ba", "Agent@2026", "O. Ba", Role.AGENT, "B", null, 30);
        user("agent.faye", "Agent@2026", "I. Faye", Role.AGENT, "A", null, 21);
        user("portail.sarr", "Portail@2026", "K. Sarr", Role.PORTAIL, "D", null, 30);
        // Comptes pour illustrer l'administration : un ancien agent désactivé, un compte verrouillé
        AppUser gone = user("agent.gueye", "Agent@2026", "P. Gueye", Role.AGENT, "C", null, 60);
        gone.setEnabled(false);
        AppUser locked = user("portail.ndiaye", "Portail@2026", "S. Ndiaye", Role.PORTAIL, "D", null, 12);
        locked.setFailedAttempts(5);
        locked.setLockedUntil(now.plus(Duration.ofMinutes(12)));
    }

    private AppUser user(String login, String pwd, String name, Role role, String zone, String email, int daysAgo) {
        AppUser u = new AppUser(login, encoder.encode(pwd), name, role, zone);
        u.setEmail(email);
        u.setCreatedAt(now.minus(Duration.ofDays(daysAgo)));
        u.setPasswordChangedAt(u.getCreatedAt());
        u.setCreatedBy(role == Role.ADMIN ? "Installation" : "F. Sow");
        if (!login.equals("agent.gueye")) u.setLastLoginAt(now.minus(Duration.ofMinutes(20 + rnd.nextInt(600))));
        return users.save(u);
    }

    /** Base existante créée avant l'administration : ajoute le compte administrateur s'il manque. */
    private void ensureAdmin() {
        if (users.findByUsername("admin").isEmpty()) {
            AppUser u = new AppUser("admin", encoder.encode("Admin@2026"), "F. Sow", Role.ADMIN, null);
            u.setCreatedBy("Installation");
            users.save(u);
            log.info("Compte administrateur ajouté : admin / Admin@2026");
        }
    }

    /** Historique d'audit fictif sur quelques jours (connexions, gestion des comptes, actions). */
    private void seedAudit() {
        audit(9 * 24 * 60, "admin", "F. Sow", "ADMIN", AuditEvent.Category.ADMINISTRATION, "UTILISATEUR_CREE", "Compte créé",
                "portail.ndiaye", "Profil PORTAIL · zone D", true);
        audit(8 * 24 * 60, "admin", "F. Sow", "ADMIN", AuditEvent.Category.ADMINISTRATION, "PROFIL_MODIFIE", "Profil modifié",
                "agent.faye", "zone B → A", true);
        audit(5 * 24 * 60, "admin", "F. Sow", "ADMIN", AuditEvent.Category.ADMINISTRATION, "COMPTE_DESACTIVE", "Compte désactivé",
                "agent.gueye", "Fin de mission", true);
        audit(2 * 24 * 60, "admin", "F. Sow", "ADMIN", AuditEvent.Category.ADMINISTRATION, "MOT_DE_PASSE_REINITIALISE",
                "Mot de passe réinitialisé", "agent.ba", "Mot de passe provisoire remis à l'utilisateur", true);
        String[][] logins = {{"superviseur", "M. Diop", "SUPERVISEUR"}, {"agent.diallo", "A. Diallo", "AGENT"},
                {"agent.ba", "O. Ba", "AGENT"}, {"portail.sarr", "K. Sarr", "PORTAIL"}, {"agent.faye", "I. Faye", "AGENT"}};
        for (int d = 3; d >= 0; d--) {
            for (String[] l : logins) {
                audit(d * 24 * 60 + 60 * 11 + rnd.nextInt(120), l[0], l[1], l[2], AuditEvent.Category.SECURITE,
                        "CONNEXION", "Connexion", l[0], null, true);
            }
        }
        for (int i = 5; i >= 1; i--) {
            audit(40 + i * 3, "portail.ndiaye", "S. Ndiaye", "PORTAIL", AuditEvent.Category.SECURITE,
                    i == 1 ? "COMPTE_VERROUILLE" : "ECHEC_CONNEXION", i == 1 ? "Compte verrouillé (5 échecs)" : "Échec de connexion",
                    "portail.ndiaye", "Mot de passe incorrect · tentative " + (6 - i) + "/5", false);
        }
        audit(95, "agent.ba", "O. Ba", "AGENT", AuditEvent.Category.SECURITE, "ACCES_REFUSE", "Accès refusé",
                "GET /api/indicators", "Droits insuffisants pour ce profil", false);
        audit(70, "superviseur", "M. Diop", "SUPERVISEUR", AuditEvent.Category.OPERATION, "FORMALITE_RECUE",
                "Formalité reçue (simulation)", "WDB9066331S772018", null, true);
        audit(66, "superviseur", "M. Diop", "SUPERVISEUR", AuditEvent.Category.OPERATION, "TICKET_EMIS",
                "Ticket de retrait émis", "WDB9066331S772018", null, true);
        audit(35, "portail.sarr", "K. Sarr", "PORTAIL", AuditEvent.Category.OPERATION, "SCAN_PORTAIL",
                "Scan au portail de sortie", null, null, true);
        audit(25, "agent.diallo", "A. Diallo", "AGENT", AuditEvent.Category.OPERATION, "STATIONNEMENT",
                "Stationnement confirmé par scan", DEMO_VIN, null, true);
    }

    private void audit(int minutesAgo, String actor, String name, String role, AuditEvent.Category cat, String action,
                       String label, String target, String details, boolean ok) {
        AuditEvent e = new AuditEvent(cat, action, label, actor, name, role, target, details, ok);
        e.setAt(now.minus(Duration.ofMinutes(minutesAgo)));
        e.setIp(role.equals("PORTAIL") ? "10.20.4.12" : "10.20.1." + (20 + rnd.nextInt(40)));
        auditEvents.save(e);
    }

    // ------------------------------------------------------------------ zones et places

    private void seedPlaces() {
        zones.save(new ParkZone("A", "Arrivages récents", "Véhicules légers", "Système"));
        zones.save(new ParkZone("B", "Zone pilote", "Véhicules légers", "Système"));
        zones.save(new ParkZone("C", "Utilitaires & engins", "Utilitaires", "Système"));
        zones.save(new ParkZone("D", "Pré-sortie", "Véhicules prêts", "Système"));
        for (String z : List.of("A", "B", "C", "D")) {
            for (int a = 1; a <= 4; a++) {
                for (int p = 1; p <= 24; p++) {
                    ParkingPlace pl = places.save(new ParkingPlace(z, a, p));
                    placeByCode.put(pl.getCode(), pl);
                }
            }
        }
    }

    // ------------------------------------------------------------------ véhicules clés (maquette)

    private void seedKeyVehicles() {
        // Le véhicule de la démonstration : localisé, BAE en attente
        Vehicle demo = vehicle(DEMO_VIN, "Berline compacte", "gris métallisé", 2021, "2026-114", "DKR-88341",
                "Moussa Ndiaye", "4417", "TR-12 Transit", "B-3-18", 2, "A. Diallo");
        demo.setClientAccessCode(props.demo().clientSmsCode());
        formalitiesFor(demo, EnumSet.of(FormalityType.BAE));
        inspection(demo, "1/4", 2, null, List.of(new Inspection.Reserve("Portière arrière gauche",
                "Rayure superficielle", "Gauche")), "A. Diallo");

        Vehicle v2 = vehicle("VF1RJA00368512947", "SUV", "blanc", 2022, "2026-114", "DKR-88342",
                "Sokhna Thiam", "1180", "Sahel Transit", "B-3-7", 2, "A. Diallo");
        formalitiesFor(v2, EnumSet.noneOf(FormalityType.class));
        inspection(v2, "1/2", 2, 18200, List.of(), "A. Diallo");

        Vehicle v3 = vehicle("JN1TBNT30U0412876", "Pick-up", "noir", 2020, "2026-114", "DKR-88110",
                "Pape Mbaye", "9021", "Sahel Transit", "B-1-4", 6, "O. Ba");
        formalitiesFor(v3, EnumSet.noneOf(FormalityType.class));
        ticket(v3, "14:00 – 15:00", Duration.ofHours(3));

        Vehicle v4 = vehicle("VF7ND9HP4LJ603185", "Citadine", "rouge", 2019, "2026-109", "DKR-87011",
                "Fatou Gaye", "3310", "Atlantique Transit", "A-2-9", 5, "I. Faye");
        formalitiesFor(v4, EnumSet.of(FormalityType.BAE, FormalityType.PAIEMENT));
        openMove(v4, "A-2-9", "I. Faye", Duration.ofMinutes(95));

        Vehicle v5 = vehicle("WDB9066331S772018", "Fourgon", "blanc", 2021, "2026-109", "DKR-87002",
                "Mamadou Fall", "5520", "Atlantique Transit", "D-2-11", 4, "K. Sarr");
        formalitiesFor(v5, EnumSet.noneOf(FormalityType.class));
        inspection(v5, "3/4", 2, 64000, List.of(
                new Inspection.Reserve("Pare-chocs avant", "Éraflure", "Avant"),
                new Inspection.Reserve("Rétroviseur droit", "Coque fissurée", "Droite")), "K. Sarr");
        Ticket t5 = ticket(v5, "09:00 – 10:00", Duration.ofHours(5));
        GatePassage gp = new GatePassage();
        gp.setVin(v5.getVin());
        gp.setTicketNumber(t5.getTicketNumber());
        gp.setLane(1);
        gp.setReadMode("RFID+QR");
        gp.setReadAt(now.minus(Duration.ofMinutes(12)));
        gp.setDecision(GateDecision.PENDING_AGENT);
        passages.save(gp);

        Vehicle v6 = vehicle("KMHD841CBMU530927", "Berline", "bleu", 2022, "2026-114", "DKR-88390",
                "Ousmane Kane", "7712", "TR-12 Transit", "B-4-21", 3, "O. Ba");
        formalitiesFor(v6, EnumSet.noneOf(FormalityType.class));
        ticket(v6, "11:00 – 12:00", Duration.ofHours(2));

        Vehicle v7 = vehicle("SJNFAAJ11U2640092", "SUV", "gris", 2023, "2026-114", "DKR-88401",
                "Aïda Sow", "6603", "Sahel Transit", "B-2-15", 2, "O. Ba");
        formalitiesFor(v7, EnumSet.of(FormalityType.BAE, FormalityType.PAIEMENT, FormalityType.BON_LIVRAISON));
        openMove(v7, "B-2-15", "O. Ba", Duration.ofMinutes(50));
        // Le portique de la zone D a lu ce véhicule : incohérence avec sa fiche
        reads.save(new ReadEvent(null, "D", v7.getVin(), ReadSource.RFID_PORTIQUE, "Portique zone D",
                now.minus(Duration.ofMinutes(43))));

        Vehicle v8 = vehicle("VF1HJD20X61188245", "Break", "noir", 2018, "2026-109", "DKR-87055",
                "Fodé Gueye", "2290", "Atlantique Transit", "A-2-10", 5, "I. Faye");
        formalitiesFor(v8, EnumSet.of(FormalityType.BON_LIVRAISON, FormalityType.BAE));

        Vehicle v9 = vehicle("ZFA31200000904311", "Citadine", "blanc", 2020, "2026-109", "DKR-86990",
                "Awa Seck", "8841", "Sahel Transit", "B-1-12", 8, "A. Diallo");
        formalitiesFor(v9, EnumSet.of(FormalityType.BAE, FormalityType.PAIEMENT));
        inspection(v9, "1/4", 1, 91000, List.of(new Inspection.Reserve("Capot", "Bosse légère", "Avant")), "A. Diallo");

        Vehicle v10 = vehicle("JTMBFREV30D118870", "SUV", "noir", 2023, "2026-114", "DKR-88420",
                "Bineta Diouf", "4471", "TR-12 Transit", "B-3-2", 2, "A. Diallo");
        formalitiesFor(v10, EnumSet.of(FormalityType.BAE));

        // Véhicule sorti ce matin
        Vehicle v11 = vehicle("WVWZZZAUZKP455210", "Compacte", "gris", 2019, "2026-104", "DKR-86001",
                "Rokhaya Ba", "1204", "Atlantique Transit", "D-1-1", 11, "K. Sarr");
        formalitiesFor(v11, EnumSet.noneOf(FormalityType.class));
        Ticket t11 = ticket(v11, null, Duration.ofHours(20));
        t11.setUsed(true);
        t11.setUsedAt(now.minus(Duration.ofMinutes(140)));
        v11.setExitedAt(now.minus(Duration.ofMinutes(140)));
        Movement exit = new Movement(v11, MovementType.SORTIE, "D-1-1", "Portail", "K. Sarr", now.minus(Duration.ofMinutes(140)));
        exit.setArrivalScannedAt(exit.getStartedAt());
        movements.save(exit);
        GatePassage gp11 = new GatePassage();
        gp11.setVin(v11.getVin());
        gp11.setTicketNumber(t11.getTicketNumber());
        gp11.setReadMode("RFID+QR");
        gp11.setReadAt(now.minus(Duration.ofMinutes(142)));
        gp11.setDecision(GateDecision.EXIT_VALIDATED);
        gp11.setDecidedBy("K. Sarr");
        gp11.setDecidedAt(now.minus(Duration.ofMinutes(140)));
        passages.save(gp11);

        Vehicle v12 = vehicle("VR3UPHNKSKT301764", "Citadine", "orange", 2024, "2026-114", "DKR-88455",
                "Cheikh Sy", "3087", "Sahel Transit", "B-4-6", 2, "O. Ba");
        formalitiesFor(v12, EnumSet.of(FormalityType.BAE, FormalityType.BON_LIVRAISON));

        // Place occupée selon la fiche, lue vide lors de l'inventaire en rafale
        Vehicle v13 = vehicle("VNKKTUD30FA227731", "Utilitaire", "blanc", 2017, "2026-104", "DKR-86120",
                "Ibrahima Ndoye", "5561", "Atlantique Transit", "C-3-7", 9, "I. Faye");
        formalitiesFor(v13, EnumSet.of(FormalityType.PAIEMENT, FormalityType.BAE));
        reads.save(new ReadEvent("C-3-7", "C", null, ReadSource.RAFALE, "I. Faye", now.minus(Duration.ofMinutes(110))));
    }

    // ------------------------------------------------------------------ remplissage du parc

    private void seedFillers() {
        String[][] models = {{"Berline", "gris"}, {"SUV", "blanc"}, {"Citadine", "rouge"}, {"Pick-up", "noir"},
                {"Utilitaire", "blanc"}, {"Break", "bleu"}, {"Compacte", "noir"}, {"Fourgon", "blanc"}};
        String[] forwarders = {"Sahel Transit", "TR-12 Transit", "Atlantique Transit", "Teranga Logistique"};
        String[] agents = {"A. Diallo", "O. Ba", "I. Faye"};
        Set<String> taken = new HashSet<>();
        vehicles.findAll().forEach(v -> {
            if (v.getPlace() != null && v.getExitedAt() == null) taken.add(v.getPlace().getCode());
        });
        int seq = 0;
        for (String z : List.of("A", "B", "C", "D")) {
            for (int a = 1; a <= 4; a++) {
                for (int p = 1; p <= 24; p++) {
                    String code = z + "-" + a + "-" + p;
                    if (taken.contains(code)) continue;
                    int h = (a * 7 + p * 13 + z.charAt(0) * 3) % 5;
                    if (h == 0 || (z.equals("C") && p % 4 == 0) || (z.equals("D") && p % 3 == 0)) continue; // place libre
                    seq++;
                    String vin = "SMDKT" + z + String.format("%011d", 30_000_000L + seq * 7919L);
                    String[] m = models[rnd.nextInt(models.length)];
                    String agent = z.equals("A") ? "I. Faye" : agents[rnd.nextInt(agents.length)];
                    int days = z.equals("A") ? 1 + rnd.nextInt(3) : z.equals("C") ? 5 + rnd.nextInt(10) : 2 + rnd.nextInt(8);
                    Vehicle v = vehicle(vin, m[0], m[1], 2016 + rnd.nextInt(9), z.equals("A") ? "2026-114" : "2026-109",
                            "DKR-" + (80000 + seq), "Client " + seq, String.valueOf(1000 + rnd.nextInt(8999)),
                            forwarders[rnd.nextInt(forwarders.length)], code, days, agent);
                    boolean ready = z.equals("D") ? rnd.nextInt(10) < 7 : rnd.nextInt(10) < 2;
                    EnumSet<FormalityType> missing = EnumSet.noneOf(FormalityType.class);
                    if (!ready) {
                        missing.add(FormalityType.BAE);
                        if (rnd.nextBoolean()) missing.add(FormalityType.PAIEMENT);
                        if (rnd.nextInt(5) == 0) missing.add(FormalityType.BON_LIVRAISON);
                    }
                    formalitiesFor(v, missing);
                    if (rnd.nextInt(3) == 0) {
                        inspection(v, "1/2", 2, 20000 + rnd.nextInt(90000), List.of(), agent);
                    }
                    // Déplacements du jour, pour l'activité horaire
                    if (!z.equals("C") && rnd.nextInt(3) == 0) {
                        Instant start = todayAt(6 + rnd.nextInt(12), rnd.nextInt(60));
                        if (start.isBefore(now)) {
                            Movement mv = new Movement(v, MovementType.DEPLACEMENT, z + "-" + a + "-" + Math.max(1, p - 1),
                                    code, agent, start);
                            mv.setArrivalScannedAt(start.plus(Duration.ofMinutes(3 + rnd.nextInt(9))));
                            movements.save(mv);
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ armoire à clés (fictif)

    private void seedKeys() {
        for (Vehicle v : vehicles.findAllOnPark()) {
            if (v.getPlace() == null || v.getVin().startsWith("SMDKT") && rnd.nextInt(3) != 0) continue;
            KeyEvent k = new KeyEvent(v.getVin(), KeyEvent.Action.DEPOSEE, "Armoire",
                    "Armoire " + v.getPlace().getZone() + " · crochet " + v.getPlace().getNumero(), "A. Diallo");
            if (v.getDisembarkedAt() != null) k.setAt(v.getDisembarkedAt().plus(Duration.ofMinutes(40)));
            keyEvents.save(k);
        }
        // Le fourgon au portail : clé sortie par l'agent de portail
        keyEvents.save(new KeyEvent("WDB9066331S772018", KeyEvent.Action.SORTIE, "K. Sarr", null, "K. Sarr"));
        // Étiquettes suspendues réutilisables sur deux véhicules de la zone pilote
        vehicles.findByVin("VF1RJA00368512947").ifPresent(v -> v.setTagReusable(true));
        vehicles.findByVin("KMHD841CBMU530927").ifPresent(v -> v.setTagReusable(true));
    }

    // ------------------------------------------------------------------ indicateurs du pilote (fictifs)

    private void seedPilotMeasures() {
        series("SEARCH_TIME_MIN", 18, 15, 11, 8, 6, 5, 4, 3.5, 3);
        series("INVENTORY_RELIABILITY_PCT", 92, 93.5, 95, 96.2, 97, 97.8, 98.4, 98.9, 99.1);
        series("PICKUP_PREP_MIN", 130, 120, 105, 90, 75, 62, 55, 48, 45);
        series("TRACE_COMPLETE_PCT", 61, 82, 90, 94, 96.5, 97.8, 98.6, 99.2, 99.5);
    }

    private void series(String code, double... values) {
        for (int w = 0; w < values.length; w++) measures.save(new PilotMeasure(code, w, values[w]));
    }

    // ------------------------------------------------------------------ utilitaires

    private Instant todayAt(int hour, int minute) {
        ZoneId dakar = ZoneId.of("Africa/Dakar");
        return LocalDate.now(dakar).atTime(hour, minute).atZone(dakar).toInstant();
    }

    private Vehicle vehicle(String vin, String model, String color, int year, String escale, String bl,
                            String beneficiary, String idSuffix, String forwarder, String placeCode, int daysAgo,
                            String agent) {
        Vehicle v = new Vehicle(vin, model, color, year, escale, bl);
        v.setBeneficiaryName(beneficiary);
        v.setBeneficiaryIdSuffix(idSuffix);
        v.setForwarder(forwarder);
        v.setRfidEpc("E280-" + vin.substring(11));
        v.setClientAccessCode(String.format("%04d", rnd.nextInt(10000)));
        Instant landed = now.minus(Duration.ofDays(daysAgo)).minus(Duration.ofMinutes(rnd.nextInt(300)));
        v.setDisembarkedAt(landed);
        ParkingPlace place = placeByCode.get(placeCode);
        v.setPlace(place);
        v.setPositionConfirmed(true);
        vehicles.save(v);

        Movement deb = new Movement(v, MovementType.DEBARQUEMENT, "Quai 2", null, agent, landed);
        deb.setArrivalScannedAt(landed);
        deb.setNote("VIN lu par photo et confirmé");
        movements.save(deb);
        Instant parkedAt = landed.plus(Duration.ofMinutes(15 + rnd.nextInt(30)));
        Movement park = new Movement(v, MovementType.STATIONNEMENT, "Quai 2", placeCode, agent, parkedAt);
        park.setArrivalScannedAt(parkedAt);
        park.setNote("Scan QR place");
        movements.save(park);

        // Dernière lecture de la place : fraîche en zone pilote, ancienne en zone C
        long minutesAgo = switch (place.getZone()) {
            case "B" -> rnd.nextInt(300);
            case "A" -> 60 + rnd.nextInt(1100);
            case "D" -> rnd.nextInt(600);
            default -> 1500 + rnd.nextInt(3000);
        };
        Instant readAt = now.minus(Duration.ofMinutes(minutesAgo));
        if (readAt.isBefore(parkedAt)) readAt = parkedAt;
        ReadSource src = place.getZone().equals("B") ? (rnd.nextInt(4) == 0 ? ReadSource.RAFALE : ReadSource.QR)
                : (place.getZone().equals("D") && rnd.nextBoolean() ? ReadSource.RFID_PORTIQUE : ReadSource.QR);
        place.setLastReadAt(readAt);
        place.setLastReadSource(src);
        reads.save(new ReadEvent(placeCode, place.getZone(), vin, src, agent, readAt));
        return v;
    }

    private void formalitiesFor(Vehicle v, Set<FormalityType> missing) {
        for (FormalityType t : FormalityType.values()) {
            Formality f = new Formality(v, t);
            if (!missing.contains(t)) {
                f.setStatus(FormalityStatus.RECEIVED);
                f.setReceivedAt(v.getDisembarkedAt().plus(Duration.ofHours(6 + rnd.nextInt(30))));
                if (f.getReceivedAt().isAfter(now)) f.setReceivedAt(now.minus(Duration.ofMinutes(30)));
                f.setSource(t == FormalityType.PAIEMENT ? "Caisse du terminal" : "Plateforme de formalités");
            }
            formalities.save(f);
        }
    }

    private void inspection(Vehicle v, String fuel, int keys, Integer km, List<Inspection.Reserve> reserves, String agent) {
        Inspection i = new Inspection();
        i.setVehicle(v);
        i.setFuelLevel(fuel);
        i.setKeysCount(keys);
        i.setMileage(km);
        for (String view : List.of("Avant", "Arrière", "Gauche", "Droite", "Toit", "Intérieur", "Compteur", "Plaque VIN")) {
            i.getPhotos().add(new Inspection.Photo(view, "demo/" + v.getVin() + "/" + view + ".jpg"));
        }
        i.getReserves().addAll(reserves);
        i.setInspectedBy(agent);
        i.setInspectedAt(v.getDisembarkedAt().plus(Duration.ofMinutes(28)));
        inspections.save(i);
    }

    private void openMove(Vehicle v, String from, String agent, Duration ago) {
        Movement m = new Movement(v, MovementType.DEPLACEMENT, from, null, agent, now.minus(ago));
        m.setNote("Déplacement démarré, arrivée non scannée");
        movements.save(m);
    }

    private Ticket ticket(Vehicle v, String slot, Duration issuedAgo) {
        Ticket t = new Ticket();
        t.setVehicle(v);
        t.setTicketNumber(String.format("TR-%d-%06d", Year.now().getValue(), ticketSeq++));
        t.setIssuedAt(now.minus(issuedAgo));
        t.setValidUntil(t.getIssuedAt().plus(Duration.ofHours(props.ticket().validityHours())));
        t.setSlot(slot);
        t.setIssuedBy("M. Diop");
        t.setQrToken(signer.sign(t.getTicketNumber(), v.getVin(), t.getValidUntil()));
        return tickets.save(t);
    }
}
