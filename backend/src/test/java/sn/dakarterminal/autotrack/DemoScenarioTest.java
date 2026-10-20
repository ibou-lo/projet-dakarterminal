package sn.dakarterminal.autotrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rejoue le scénario de démonstration de 3 minutes contre l'API sécurisée.
 */
@SpringBootTest
@ActiveProfiles("h2") // base H2 en mémoire : les tests ne dépendent pas d'un PostgreSQL local
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DemoScenarioTest {

    private static final String VIN = "VF3LCYHZPJS218456";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    private String login(String user, String pwd) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"" + pwd + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    @Test
    void anonymousCallsAreRejected() throws Exception {
        mvc.perform(get("/api/vehicles")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"superviseur\",\"password\":\"faux\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        String agent = login("agent.diallo", "Agent@2026");
        mvc.perform(get("/api/indicators").header("Authorization", agent)).andExpect(status().isForbidden());
        mvc.perform(post("/api/vehicles/" + VIN + "/formalities/BAE/receive").header("Authorization", agent))
                .andExpect(status().isForbidden());

        String client = mvc.perform(post("/api/client/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blNumber\":\"DKR-88341\",\"smsCode\":\"5821\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String clientToken = "Bearer " + json.readTree(client).get("accessToken").asText();
        mvc.perform(get("/api/vehicles").header("Authorization", clientToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/client/me").header("Authorization", clientToken)).andExpect(status().isOk());

        // Agent de portail : portique, tickets, véhicules, clés ; pas les pages de l'équipe de parc
        String gate = login("portail.sarr", "Portail@2026");
        mvc.perform(get("/api/gate/passages").header("Authorization", gate)).andExpect(status().isOk());
        mvc.perform(get("/api/tickets").header("Authorization", gate)).andExpect(status().isOk());
        mvc.perform(get("/api/vehicles").header("Authorization", gate)).andExpect(status().isOk());
        mvc.perform(get("/api/keys").header("Authorization", gate)).andExpect(status().isOk());
        mvc.perform(get("/api/movements").header("Authorization", gate)).andExpect(status().isForbidden());
        mvc.perform(get("/api/pickups/board").header("Authorization", gate)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tags").header("Authorization", gate)).andExpect(status().isForbidden());
        mvc.perform(get("/api/indicators").header("Authorization", gate)).andExpect(status().isForbidden());
        mvc.perform(post("/api/vehicles/" + VIN + "/move").header("Authorization", gate)).andExpect(status().isForbidden());
        // Agent de parc : pas le portique
        mvc.perform(get("/api/gate/passages").header("Authorization", agent)).andExpect(status().isForbidden());
    }

    @Test
    void fullDemoScenario() throws Exception {
        String sup = login("superviseur", "Superviseur@2026");

        JsonNode passport = read(mvc.perform(get("/api/vehicles/" + VIN).header("Authorization", sup))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(passport.at("/vehicle/status").asText()).isEqualTo("AWAITING_AUTHORIZATION");
        assertThat(passport.at("/vehicle/placeCode").asText()).isEqualTo("B-3-18");

        // Ticket refusé tant que le BAE manque
        mvc.perform(post("/api/vehicles/" + VIN + "/ticket").header("Authorization", sup)).andExpect(status().isConflict());

        // Réception simulée du BAE → « Prêt à enlever »
        passport = read(mvc.perform(post("/api/vehicles/" + VIN + "/formalities/BAE/receive").header("Authorization", sup))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(passport.at("/vehicle/status").asText()).isEqualTo("READY");

        JsonNode ticket = read(mvc.perform(post("/api/vehicles/" + VIN + "/ticket").header("Authorization", sup))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String qr = ticket.get("qrToken").asText();
        assertThat(qr).startsWith("TKT.");

        String gateAgent = login("portail.sarr", "Portail@2026");
        JsonNode gate = read(mvc.perform(post("/api/gate/scan").header("Authorization", gateAgent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketQr\":\"" + qr + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(gate.get("decision").asText()).isEqualTo("PENDING_AGENT");

        mvc.perform(post("/api/gate/passages/" + gate.get("passageId").asLong() + "/decision")
                        .header("Authorization", gateAgent).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"EXIT_VALIDATED\"}"))
                .andExpect(status().isOk());

        // Le ticket ne peut pas servir deux fois
        JsonNode again = read(mvc.perform(post("/api/gate/scan").header("Authorization", gateAgent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketQr\":\"" + qr + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(again.get("decision").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void assistantAnswersFromData() throws Exception {
        String sup = login("superviseur", "Superviseur@2026");
        JsonNode reply = read(mvc.perform(post("/api/assistant/chat").header("Authorization", sup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Où est le véhicule …218456 et que manque-t-il pour son enlèvement ?\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(reply.get("text").asText()).contains("zone B, allée 3, place 18").containsIgnoringCase("bon à enlever");
    }

    @Test
    void unauthorizedVehicleIsBlockedAtGate() throws Exception {
        String gateAgent = login("portail.sarr", "Portail@2026");
        JsonNode gate = read(mvc.perform(post("/api/gate/scan").header("Authorization", gateAgent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vin\":\"ZFA31200000904311\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(gate.get("decision").asText()).isEqualTo("BLOCKED");
    }

    @Test
    void typedTicketNumberRequiresManualCheck() throws Exception {
        String sup = login("superviseur", "Superviseur@2026");
        mvc.perform(post("/api/vehicles/" + VIN + "/formalities/BAE/receive").header("Authorization", sup)).andExpect(status().isOk());
        JsonNode ticket = read(mvc.perform(post("/api/vehicles/" + VIN + "/ticket").header("Authorization", sup))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String number = ticket.get("ticketNumber").asText();

        String gateAgent = login("portail.sarr", "Portail@2026");
        // Numéro tapé à la main (QR illisible) : sortie possible, mais contrôle manuel du ticket
        JsonNode typed = read(mvc.perform(post("/api/gate/scan").header("Authorization", gateAgent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ticketQr\":\"" + number.toLowerCase() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(typed.get("decision").asText()).isEqualTo("PENDING_AGENT");
        assertThat(typed.toString()).contains("\"code\":\"TICKET\"").contains("MANUAL");

        // Étiquette véhicule scannée (VH:<VIN>) : le ticket reste à présenter
        JsonNode label = read(mvc.perform(post("/api/gate/scan").header("Authorization", gateAgent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vin\":\"VH:" + VIN + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(label.get("decision").asText()).isEqualTo("PENDING_AGENT");
    }

    @Test
    void supervisorCreatesZone() throws Exception {
        String sup = login("superviseur", "Superviseur@2026");
        String body = "{\"code\":\"e\",\"label\":\"Extension quai 3\",\"allees\":2,\"placesParAllee\":10}";
        JsonNode zone = read(mvc.perform(post("/api/zones").header("Authorization", sup)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(zone.get("code").asText()).isEqualTo("E");
        assertThat(zone.get("places").asInt()).isEqualTo(20);
        // Doublon refusé ; agent non autorisé
        mvc.perform(post("/api/zones").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/zones").header("Authorization", login("agent.diallo", "Agent@2026"))
                .contentType(MediaType.APPLICATION_JSON).content(body.replace("\"e\"", "\"F\""))).andExpect(status().isForbidden());
        // Ajout d'une allée, visible sur le plan
        zone = read(mvc.perform(post("/api/zones/E/allees").header("Authorization", sup)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"count\":1,\"placesParAllee\":12}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(zone.get("allees").asInt()).isEqualTo(3);
        String park = mvc.perform(get("/api/park").header("Authorization", sup)).andReturn().getResponse().getContentAsString();
        assertThat(park).contains("\"zone\":\"E\"").contains("E-3-12");
        // Zone B utilisée : suppression refusée ; zone E vide : supprimée
        mvc.perform(delete("/api/zones/B").header("Authorization", sup)).andExpect(status().isConflict());
        mvc.perform(delete("/api/zones/E").header("Authorization", sup)).andExpect(status().isNoContent());
    }

    @Test
    void mobileScanFormats() throws Exception {
        String agent = login("agent.diallo", "Agent@2026");
        // Le véhicule se retrouve par VH:…, KEY:…, code DAT, fin de VIN ou EPC
        for (String code : new String[]{"VH:" + VIN, "KEY:" + VIN, "dat-218456", "218456", "E280-218456"}) {
            JsonNode p = read(mvc.perform(get("/api/vehicles/" + code).header("Authorization", agent))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertThat(p.at("/vehicle/vin").asText()).isEqualTo(VIN);
        }
        // QR d'une place scanné à la place du véhicule : message explicite
        mvc.perform(get("/api/vehicles/PL:B-3-18").header("Authorization", agent)).andExpect(status().isBadRequest());

        // Déplacement : démarré deux fois (nouvel essai) → un seul mouvement ouvert, clôturé par le scan de la place
        mvc.perform(post("/api/vehicles/DAT-218456/move").header("Authorization", agent)).andExpect(status().isOk());
        mvc.perform(post("/api/vehicles/DAT-218456/move").header("Authorization", agent)).andExpect(status().isOk());
        // Mauvais QR à l'étape place : refusé
        mvc.perform(post("/api/vehicles/" + VIN + "/park").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"placeScan\":\"VH:" + VIN + "\"}")).andExpect(status().isBadRequest());
        JsonNode free = read(mvc.perform(get("/api/park/suggest-place?zone=C").header("Authorization", agent))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String qr = free.get("qrPayload").asText();            // « PL:C-… », envoyé en minuscules comme un lecteur mal réglé
        JsonNode parked = read(mvc.perform(post("/api/vehicles/" + VIN + "/park").header("Authorization", agent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"placeScan\":\" " + qr.toLowerCase() + " \"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(parked.at("/vehicle/placeCode").asText()).isEqualTo(free.get("placeCode").asText());
        String missing = mvc.perform(get("/api/movements/missing-arrivals").header("Authorization", agent))
                .andReturn().getResponse().getContentAsString();
        assertThat(missing).doesNotContain(VIN);

        // Portail : QR du porte-clé → véhicule reconnu, ticket à présenter
        String gateAgent = login("portail.sarr", "Portail@2026");
        JsonNode gate = read(mvc.perform(post("/api/gate/scan").header("Authorization", gateAgent)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"vin\":\"KEY:" + VIN + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(gate.toString()).contains(VIN.substring(11));
    }

    @Test
    void adminManagesUsersAndAudit() throws Exception {
        String admin = login("admin", "Admin@2026");
        String sup = login("superviseur", "Superviseur@2026");
        String agent = login("agent.diallo", "Agent@2026");

        // Droits : comptes = administrateur ; audit = administrateur + superviseur ; opérations interdites à l'administrateur
        mvc.perform(get("/api/admin/users").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", sup)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").header("Authorization", sup)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/audit").header("Authorization", agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/indicators").header("Authorization", admin)).andExpect(status().isForbidden());

        // Création : mot de passe provisoire utilisable, puis changé par l'utilisateur
        JsonNode created = read(mvc.perform(post("/api/admin/users").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Agent.Test\",\"fullName\":\"T. Test\",\"role\":\"AGENT\",\"zone\":\"b\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(created.at("/user/username").asText()).isEqualTo("agent.test");
        String tmp = created.get("temporaryPassword").asText();
        String testToken = login("agent.test", tmp);
        // Mot de passe provisoire : tout est fermé sauf « Mon compte »
        mvc.perform(get("/api/vehicles").header("Authorization", testToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password").header("Authorization", testToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + tmp + "\",\"newPassword\":\"court\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/password").header("Authorization", testToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + tmp + "\",\"newPassword\":\"NouveauMdp2026\"}")).andExpect(status().isOk());
        String fresh = login("agent.test", "NouveauMdp2026");
        mvc.perform(get("/api/vehicles").header("Authorization", fresh)).andExpect(status().isOk());

        // Désactivation : le jeton en cours est refusé immédiatement, la connexion aussi
        mvc.perform(post("/api/admin/users/agent.test/disable").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/vehicles").header("Authorization", testToken)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"agent.test\",\"password\":\"NouveauMdp2026\"}")).andExpect(status().isUnauthorized());

        // Garde-fous : pas d'auto-désactivation
        mvc.perform(post("/api/admin/users/admin/disable").header("Authorization", admin)).andExpect(status().isBadRequest());

        // Verrouillage après 5 échecs, puis déverrouillage par l'administrateur
        for (int i = 1; i <= 5; i++) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"agent.ba\",\"password\":\"faux\"}"))
                    .andExpect(i < 5 ? status().isUnauthorized() : status().isTooManyRequests());
        }
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"agent.ba\",\"password\":\"Agent@2026\"}")).andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/admin/users/agent.ba/unlock").header("Authorization", admin)).andExpect(status().isOk());
        login("agent.ba", "Agent@2026");

        // Journal d'audit
        String audit = mvc.perform(get("/api/admin/audit?days=1").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("UTILISATEUR_CREE").contains("COMPTE_DESACTIVE").contains("COMPTE_VERROUILLE")
                .contains("MOT_DE_PASSE_CHANGE").contains("ACCES_REFUSE");
    }

    @Test
    void adminEditsPrivileges() throws Exception {
        String admin = login("admin", "Admin@2026");
        String gate = login("portail.sarr", "Portail@2026");
        mvc.perform(get("/api/movements").header("Authorization", gate)).andExpect(status().isForbidden());

        JsonNode m = read(mvc.perform(get("/api/admin/privileges").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(m.at("/grants/PORTAIL").toString()).doesNotContain("PAGE_MOUVEMENTS");

        // L'administrateur ouvre la page Mouvements à l'agent de portail : effet immédiat, sans reconnexion
        java.util.List<String> portail = new java.util.ArrayList<>();
        m.at("/grants/PORTAIL").forEach(n -> portail.add(n.asText()));
        portail.add("PAGE_MOUVEMENTS");
        String body = "{\"grants\":{\"PORTAIL\":" + json.writeValueAsString(portail) + "}}";
        mvc.perform(put("/api/admin/privileges").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/movements").header("Authorization", gate)).andExpect(status().isOk());
        String me = mvc.perform(get("/api/auth/me").header("Authorization", gate)).andReturn().getResponse().getContentAsString();
        assertThat(me).contains("PAGE_MOUVEMENTS");

        // Privilège verrouillé : l'administrateur ne peut pas se retirer la gestion des privilèges
        mvc.perform(put("/api/admin/privileges").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"grants\":{\"ADMIN\":[\"PAGE_UTILISATEURS\"]}}")).andExpect(status().isBadRequest());
        // Seul l'administrateur modifie la matrice
        mvc.perform(put("/api/admin/privileges").header("Authorization", login("superviseur", "Superviseur@2026"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());

        // Retour aux valeurs par défaut
        mvc.perform(post("/api/admin/privileges/reset").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/movements").header("Authorization", gate)).andExpect(status().isForbidden());
        String audit = mvc.perform(get("/api/admin/audit?days=1").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        assertThat(audit).contains("PRIVILEGES_MODIFIES").contains("PRIVILEGES_REINITIALISES");
    }

    @Test
    void securityHardening() throws Exception {
        // Déconnexion côté serveur : le jeton est refusé ensuite
        String agent = login("agent.diallo", "Agent@2026");
        mvc.perform(get("/api/vehicles").header("Authorization", agent)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", agent)).andExpect(status().isOk());
        mvc.perform(get("/api/vehicles").header("Authorization", agent)).andExpect(status().isUnauthorized());

        // En-têtes de sécurité sur l'API
        mvc.perform(get("/api/vehicles").header("Authorization", login("superviseur", "Superviseur@2026")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Security-Policy", org.hamcrest.Matchers.containsString("default-src 'none'")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Referrer-Policy", "no-referrer"));

        // Compte désactivé : même message qu'un mauvais mot de passe (pas d'énumération)
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent.gueye\",\"password\":\"Agent@2026\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Identifiant ou mot de passe incorrect");

        // Limitation de débit par adresse IP sur la connexion client
        int last = 0;
        for (int i = 0; i < 12; i++) {
            last = mvc.perform(post("/api/client/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"blNumber\":\"XX-" + i + "\",\"smsCode\":\"0000\"}")).andReturn().getResponse().getStatus();
        }
        assertThat(last).isEqualTo(429);
    }

    private JsonNode read(String s) throws Exception {
        return json.readTree(s);
    }
}
