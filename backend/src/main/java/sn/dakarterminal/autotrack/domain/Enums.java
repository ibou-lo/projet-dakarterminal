package sn.dakarterminal.autotrack.domain;

/**
 * Énumérations du domaine, regroupées pour la lisibilité du prototype.
 */
public final class Enums {

    private Enums() {
    }

    /** Rôles applicatifs, portés par le JWT. */
    /** ADMIN : gestion des comptes et audit, sans accès aux opérations du parc (séparation des tâches). */
    public enum Role { SUPERVISEUR, AGENT, PORTAIL, CLIENT, ADMIN }

    /** Formalités nécessaires à l'enlèvement. */
    public enum FormalityType {
        BL("Manifeste & connaissement (BL)"),
        BON_LIVRAISON("Bon de livraison armateur"),
        PAIEMENT("Paiement des frais de parc"),
        BAE("Bon à enlever douane (BAE)"),
        BENEFICIAIRE("Bénéficiaire autorisé");

        private final String label;

        FormalityType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum FormalityStatus { PENDING, RECEIVED }

    /** Statut calculé d'un véhicule : présence physique + disponibilité administrative. */
    public enum VehicleStatus {
        AWAITING_AUTHORIZATION("Autorisation en attente"),
        READY("Prêt à enlever"),
        POSITION_TO_CONFIRM("Position à confirmer"),
        EXITED("Sorti");

        private final String label;

        VehicleStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /** Étape du tableau des enlèvements. */
    public enum PickupStage {
        FORMALITES_EN_COURS, AUTORISE_TICKET_A_EMETTRE, TICKET_EMIS, AU_PORTAIL, SORTI
    }

    public enum MovementType { DEBARQUEMENT, STATIONNEMENT, DEPLACEMENT, PRE_SORTIE, SORTIE }

    /** Origine d'une lecture de position (carte « fraîcheur »). */
    public enum ReadSource { QR, RFID_PORTIQUE, RAFALE, MANUEL }

    public enum GateDecision { PENDING_AGENT, EXIT_VALIDATED, BLOCKED }

    public enum TaskStatus { OPEN, DONE }

    public enum ActionStatus { PROPOSED, CONFIRMED, CANCELLED }
}
