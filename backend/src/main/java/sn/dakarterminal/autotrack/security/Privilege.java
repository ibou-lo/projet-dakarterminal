package sn.dakarterminal.autotrack.security;

import sn.dakarterminal.autotrack.domain.Enums.Role;

import java.util.EnumSet;
import java.util.Set;

import static sn.dakarterminal.autotrack.domain.Enums.Role.*;

/**
 * Catalogue des privilèges attribuables aux profils depuis l'administration.
 * - PAGE : accès à un écran du poste web (et aux données sensibles de cet écran) ;
 * - ACTION : opération contrôlée par le serveur (@PreAuthorize("hasAuthority('…')")).
 * Chaque privilège a des profils par défaut ; « locked » = profils qui ne peuvent pas le perdre
 * (évite qu'un administrateur se retire l'accès à l'administration).
 * Le nom de l'énumération est l'autorité Spring Security : ne pas renommer sans migrer la table role_privilege.
 */
public enum Privilege {

    // ------------------------------------------------------------------ Pages
    PAGE_PARC(Kind.PAGE, "Opérations", "Plan du parc & passeport", "Plan des zones, passeport numérique, assistant de recherche",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    PAGE_VEHICULES(Kind.PAGE, "Opérations", "Liste des véhicules", "Recherche, filtres par statut et par zone",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    PAGE_AFFECTATION(Kind.PAGE, "Opérations", "Affectation", "Placer un véhicule sur le plan depuis le poste web",
            set(SUPERVISEUR, AGENT)),
    PAGE_MOUVEMENTS(Kind.PAGE, "Opérations", "Mouvements", "Journal des déplacements, régularisations, activité des agents",
            set(SUPERVISEUR, AGENT)),
    PAGE_ENLEVEMENTS(Kind.PAGE, "Sorties", "Enlèvements", "Tableau formalités → ticket → portail → sortie",
            set(SUPERVISEUR, AGENT)),
    PAGE_TICKETS(Kind.PAGE, "Sorties", "Tickets de retrait", "Tickets émis, utilisés, expirés",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    PAGE_PORTIQUE(Kind.PAGE, "Sorties", "Portique", "Contrôle de sortie, passages, portiques RFID",
            set(SUPERVISEUR, PORTAIL)),
    PAGE_ETIQUETTES(Kind.PAGE, "QR & RFID", "Étiquettes", "Étiquettes hybrides QR + RFID, étiquettes de places",
            set(SUPERVISEUR, AGENT)),
    PAGE_CLES(Kind.PAGE, "QR & RFID", "Armoire à clés", "Qui détient quelle clé",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    PAGE_FRAICHEUR(Kind.PAGE, "QR & RFID", "Fraîcheur des lectures", "Carte des dernières lectures, incohérences",
            set(SUPERVISEUR, AGENT)),
    PAGE_INDICATEURS(Kind.PAGE, "Pilotage", "Indicateurs pilote", "Tableau de bord du pilote",
            set(SUPERVISEUR)),
    PAGE_ASSISTANT(Kind.PAGE, "Pilotage", "Assistant IA", "Chat de recherche et actions proposées",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    PAGE_UTILISATEURS(Kind.PAGE, "Administration", "Utilisateurs", "Liste et gestion des comptes",
            set(ADMIN), set(ADMIN)),
    PAGE_PROFILS(Kind.PAGE, "Administration", "Profils & privilèges", "Matrice des droits par profil",
            set(ADMIN), set(ADMIN)),
    PAGE_AUDIT(Kind.PAGE, "Administration", "Journal d'audit", "Connexions, refus d'accès, actions",
            set(ADMIN, SUPERVISEUR)),

    // ------------------------------------------------------------------ Actions
    VEHICULE_RECEPTIONNER(Kind.ACTION, "Parc", "Réceptionner un véhicule", "Lecture du VIN, création du passeport, état, photos",
            set(SUPERVISEUR, AGENT)),
    VEHICULE_STATIONNER(Kind.ACTION, "Parc", "Stationner / déplacer / affecter", "Scan de place, déplacement, affectation web",
            set(SUPERVISEUR, AGENT)),
    INVENTAIRE_RAFALE(Kind.ACTION, "Parc", "Inventaire en rafale", "Envoyer les lectures d'une allée",
            set(SUPERVISEUR, AGENT)),
    TACHE_GERER(Kind.ACTION, "Parc", "Tâches de vérification", "Créer et clôturer des tâches pour les agents",
            set(SUPERVISEUR, AGENT)),
    ZONE_GERER(Kind.ACTION, "Parc", "Gérer les zones du plan", "Créer, renommer, agrandir, supprimer une zone",
            set(SUPERVISEUR)),
    FORMALITE_SIMULER(Kind.ACTION, "Sorties", "Simuler une formalité (BAE…)", "Réception / réinitialisation simulée d'une formalité",
            set(SUPERVISEUR)),
    TICKET_EMETTRE(Kind.ACTION, "Sorties", "Émettre un ticket de retrait", "Ticket QR signé pour un véhicule prêt",
            set(SUPERVISEUR, AGENT)),
    TRANSITAIRE_RELANCER(Kind.ACTION, "Sorties", "Relancer le transitaire", "Notification d'une formalité manquante",
            set(SUPERVISEUR)),
    SORTIE_CONTROLER(Kind.ACTION, "Sorties", "Contrôler et valider une sortie", "Scan au portail, validation ou blocage",
            set(SUPERVISEUR, PORTAIL)),
    ETIQUETTE_ASSOCIER(Kind.ACTION, "QR & RFID", "Associer une étiquette RFID", "Lier une puce au passeport",
            set(SUPERVISEUR, AGENT)),
    CLE_MOUVEMENTER(Kind.ACTION, "QR & RFID", "Mouvements de clés", "Sortir, déposer, remettre une clé",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    IA_ACTION_VALIDER(Kind.ACTION, "Pilotage", "Valider une action proposée par l'IA", "Confirmer ou annuler une action de l'assistant",
            set(SUPERVISEUR, AGENT, PORTAIL)),
    UTILISATEURS_GERER(Kind.ACTION, "Administration", "Gérer les comptes", "Créer, modifier, désactiver, déverrouiller",
            set(ADMIN), set(ADMIN)),
    PRIVILEGES_GERER(Kind.ACTION, "Administration", "Gérer les privilèges des profils", "Modifier cette matrice",
            set(ADMIN), set(ADMIN));

    public enum Kind { PAGE, ACTION }

    /** Profils configurables (le client a un espace mobile à part, non configurable). */
    public static final Set<Role> CONFIGURABLE_ROLES = EnumSet.of(ADMIN, SUPERVISEUR, AGENT, PORTAIL);

    private final Kind kind;
    private final String group;
    private final String label;
    private final String description;
    private final Set<Role> defaults;
    private final Set<Role> locked;

    Privilege(Kind kind, String group, String label, String description, Set<Role> defaults) {
        this(kind, group, label, description, defaults, EnumSet.noneOf(Role.class));
    }

    Privilege(Kind kind, String group, String label, String description, Set<Role> defaults, Set<Role> locked) {
        this.kind = kind;
        this.group = group;
        this.label = label;
        this.description = description;
        this.defaults = defaults;
        this.locked = locked;
    }

    private static Set<Role> set(Role... roles) {
        return EnumSet.copyOf(java.util.List.of(roles));
    }

    public Kind kind() { return kind; }
    public String group() { return group; }
    public String label() { return label; }
    public String description() { return description; }
    public Set<Role> defaults() { return defaults; }
    public Set<Role> locked() { return locked; }
}
