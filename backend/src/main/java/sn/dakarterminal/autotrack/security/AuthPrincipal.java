package sn.dakarterminal.autotrack.security;

import sn.dakarterminal.autotrack.domain.Enums.Role;

/**
 * Identité extraite du JWT, disponible dans les contrôleurs via
 * {@code @AuthenticationPrincipal AuthPrincipal me}.
 *
 * @param username identifiant (ou « client:BL » pour un client)
 * @param fullName nom affiché, utilisé dans la traçabilité
 * @param role     rôle applicatif
 * @param zone     zone d'affectation (peut être null)
 * @param bl       numéro de BL (clients uniquement)
 */
public record AuthPrincipal(String username, String fullName, Role role, String zone, String bl) {

    public boolean isSupervisor() {
        return role == Role.SUPERVISEUR;
    }
}
