package sn.dakarterminal.autotrack.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Réglage d'un privilège pour un profil, modifié depuis l'administration.
 * Pas de ligne = valeur par défaut du catalogue (security.Privilege) : un nouveau privilège
 * ajouté au code prend donc sa valeur par défaut sans migration.
 */
@Entity
@Table(name = "role_privilege", uniqueConstraints = @UniqueConstraint(columnNames = {"role", "privilege"}))
@Getter
@Setter
@NoArgsConstructor
public class RolePrivilege {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, length = 40)
    private String privilege;

    private boolean granted;

    private Instant updatedAt;

    @Column(length = 120)
    private String updatedBy;

    public RolePrivilege(String role, String privilege, boolean granted, String updatedBy) {
        this.role = role;
        this.privilege = privilege;
        this.granted = granted;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
