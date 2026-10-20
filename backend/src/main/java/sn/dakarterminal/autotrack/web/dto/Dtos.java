package sn.dakarterminal.autotrack.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Objets échangés par l'API REST (records Java, sérialisés en JSON). */
public final class Dtos {

    private Dtos() {
    }

    // ---------- Authentification ----------

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ClientLoginRequest(@NotBlank String blNumber, @NotBlank String smsCode) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, UserInfo user) {
    }

    /** privileges : privilèges effectifs du profil (menus et boutons du poste web / du mobile). */
    public record UserInfo(String username, String fullName, String role, String zone, String bl, List<String> privileges,
                           boolean mustChangePassword) {
    }

    // ---------- Véhicules ----------

    public record LastScan(Instant at, String by, String source) {
    }

    public record VehicleSummary(String vin, String shortCode, String model, String color, Integer modelYear,
                                 String escale, String blNumber, String placeCode, String placeLabel, String zone,
                                 String status, String statusLabel, String stage, int formalitiesDone,
                                 int formalitiesTotal, int reservesCount, long daysOnPark, LastScan lastScan) {
    }

    public record FormalityDto(String type, String label, String status, Instant receivedAt, String reference,
                               String source) {
    }

    public record PhotoDto(String view, String storageRef) {
    }

    public record ReserveDto(String location, String description, String photoView) {
    }

    public record InspectionDto(String fuelLevel, Integer keysCount, Integer mileage, List<PhotoDto> photos,
                                List<ReserveDto> reserves, String inspectedBy, Instant inspectedAt) {
    }

    public record HistoryItem(Instant at, String kind, String what, String who) {
    }

    public record TicketDto(String ticketNumber, String vin, String shortCode, String model, String placeLabel,
                            String beneficiary, Instant issuedAt, Instant validUntil, String slot, boolean used,
                            String qrToken) {
    }

    public record Passport(VehicleSummary vehicle, String beneficiaryMasked, String forwarder, String rfidEpc,
                           List<FormalityDto> formalities, InspectionDto inspection, List<HistoryItem> history,
                           TicketDto ticket, List<String> activeAlerts) {
    }

    public record ReceptionRequest(
            @NotBlank @Size(min = 17, max = 17) @Pattern(regexp = "[A-HJ-NPR-Z0-9]{17}",
                    message = "VIN invalide (17 caractères, sans I, O ni Q)") String vin,
            String model, String color, Integer modelYear, String escale, String blNumber,
            String beneficiaryName, String beneficiaryIdSuffix, String forwarder) {
    }

    public record InspectionRequest(String fuelLevel, Integer keysCount, Integer mileage, List<PhotoDto> photos,
                                    List<ReserveDto> reserves) {
    }

    /** Scan du QR d'une place (« PL:B-3-18 ») ou saisie de son code (« B-3-18 »). */
    public record ParkRequest(@NotBlank String placeScan) {
    }

    /** Affectation depuis le web : place choisie sur le plan ; confirmed = vérifiée sur place. */
    public record AssignRequest(@NotBlank String placeCode, boolean confirmed) {
    }

    // ---------- VIN (lecture assistée) ----------

    public record VinCheckRequest(@NotBlank String rawText) {
    }

    public record VinCheckResponse(String vin, boolean validFormat, boolean checkDigitOk, List<Integer> uncertainPositions,
                                   List<String> messages, boolean alreadyKnown) {
    }

    // ---------- Parc ----------

    public record PlaceCell(String code, int allee, int numero, String state, String vin, String shortCode,
                            Instant lastReadAt, String lastReadSource, String freshness, boolean incoherent) {
    }

    public record ZoneMap(String zone, String label, int total, int occupied, int ready, int freshPct,
                          List<List<PlaceCell>> allees) {
    }

    public record ParkOverview(long vehiclesOnPark, int confirmedPositionsPct, long readyToPickUp,
                               long movements24h, List<ZoneMap> zones) {
    }

    public record PlaceSuggestion(String placeCode, String placeLabel, String qrPayload, int freePlacesInZone) {
    }

    public record Incoherence(String kind, String vin, String placeCode, String message, Instant at) {
    }

    // ---------- Formalités / tickets ----------

    public record FormalityUpdate(String reference, String source) {
    }

    public record TicketRequest(String slot) {
    }

    // ---------- Mouvements ----------

    public record MovementDto(Long id, Instant startedAt, String vin, String shortCode, String type, String fromPlace,
                              String toPlace, String agent, boolean traceComplete, Instant arrivalScannedAt) {
    }

    public record MovementStats(long today, double traceCompletePct, long missingArrivals, Double avgMoveMinutes,
                                Map<String, Long> perHour) {
    }

    public record TaskRequest(String vin, String placeCode, @NotBlank String reason, String assignedTo) {
    }

    public record TaskDto(Long id, String vin, String placeCode, String reason, String assignedTo, String status,
                          Instant createdAt) {
    }

    public record AgentActivity(String agent, long movements) {
    }

    // ---------- Enlèvements ----------

    public record PickupCard(String vin, String shortCode, String model, String placeLabel, String client,
                             List<String> missing, String meta, boolean canIssueTicket, boolean canRemind) {
    }

    public record PickupColumn(String stage, String title, String hint, long count, List<PickupCard> cards) {
    }

    // ---------- Portail ----------

    public record GateScanRequest(String ticketQr, String rfidEpc, String vin, Integer lane) {
    }

    public record GateCheck(String code, String label, String note, String result) {
    }

    public record GateResult(Long passageId, String vin, String ticketNumber, String decision, String title,
                             String subtitle, List<GateCheck> checks) {
    }

    public record GateDecisionRequest(@NotBlank String decision, String reason) {
    }

    public record GatePassageDto(Long id, Instant readAt, String vin, String ticketNumber, int lane, String decision,
                                 String reason, String decidedBy) {
    }

    // ---------- Inventaire en rafale ----------

    public record SweepRead(String placeCode, String vin) {
    }

    public record SweepRequest(@NotBlank String zone, int allee, List<SweepRead> reads) {
    }

    public record SweepGap(String kind, String placeCode, String vin, String expectedPlace, String message) {
    }

    public record SweepResult(int placesRead, int placesTotal, int conform, int emptyConfirmed, List<SweepGap> gaps,
                              List<String> notRead) {
    }

    // ---------- Indicateurs ----------

    public record IndicatorSeries(String code, String title, String unit, double reference, double current,
                                  String delta, boolean lowerIsBetter, List<Double> weeks, String method) {
    }

    public record Indicators(boolean fictitious, List<IndicatorSeries> series) {
    }

    // ---------- Assistant ----------

    public record ChatRequest(@NotBlank String message, String contextVin) {
    }

    public record ActionProposal(Long id, String type, String vin, String description, String status) {
    }

    public record ChatTable(List<String> headers, List<List<String>> rows) {
    }

    public record ChatReply(String text, VehicleSummary vehicle, ChatTable table, ActionProposal action,
                            List<String> sources, List<String> suggestions) {
    }

    // ---------- Client ----------

    public record ClientStatus(String shortCode, String model, String color, String statusLabel, boolean ready,
                               List<String> missing, List<String> done, TicketDto ticket, boolean notifyBySms,
                               int queueAhead, String gateHours) {
    }

    // ---------- Innovations QR & RFID ----------

    public record TagRequest(@NotBlank String epc, boolean reusable) {
    }

    public record TagDto(String vin, String shortCode, String model, String placeLabel, String epc, boolean reusable,
                         String vehicleQr, String keyQr) {
    }

    public record PlaceLabel(String code, String label, String qrPayload) {
    }

    public record KeyActionRequest(@NotBlank String action, String holder, String slot) {
    }

    public record KeyStatus(String vin, String shortCode, String model, String placeLabel, String state, String holder,
                            String slot, Instant since, String keyQr) {
    }

    // ---------- Zones du parc ----------

    public record ZoneCreateRequest(@NotBlank @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z][A-Za-z0-9]{0,2}",
            message = "Code de zone : 1 à 3 caractères, commençant par une lettre") String code,
                                    @NotBlank @jakarta.validation.constraints.Size(max = 60) String label,
                                    String usage,
                                    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(20) int allees,
                                    @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(60) int placesParAllee) {
    }

    public record ZoneUpdateRequest(@NotBlank @jakarta.validation.constraints.Size(max = 60) String label, String usage) {
    }

    public record AlleeAddRequest(@jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(10) int count,
                                  @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(60) int placesParAllee) {
    }

    public record ZoneInfo(String code, String label, String usage, int allees, long places, long occupied,
                           boolean deletable, String createdBy, Instant createdAt) {
    }

    // ---------- Administration ----------

    public record AuditDto(Long id, Instant at, String actor, String actorName, String actorRole, String category,
                           String action, String label, String target, String details, String ip, boolean success) {
    }

    public record AdminUserDto(String username, String fullName, String role, String zone, String email, String phone,
                               boolean enabled, boolean locked, int failedAttempts, Instant lockedUntil,
                               Instant createdAt, String createdBy, Instant lastLoginAt, Instant passwordChangedAt) {
    }

    public record UserCreateRequest(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,40}", message = "3 à 40 caractères : lettres, chiffres, point, tiret") String username,
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank String role,
            @Size(max = 5) String zone,
            @Size(max = 120) String email,
            @Size(max = 30) String phone) {
    }

    public record UserUpdateRequest(@NotBlank @Size(max = 120) String fullName, @NotBlank String role,
                                    @Size(max = 5) String zone, @Size(max = 120) String email, @Size(max = 30) String phone) {
    }

    /** Mot de passe provisoire : affiché une seule fois à l'administrateur, jamais stocké en clair. */
    public record UserWithPassword(AdminUserDto user, String temporaryPassword) {
    }

    public record PasswordChangeRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record AdminOverview(long users, long activeUsers, long lockedUsers, Map<String, Long> usersByRole,
                                long logins24h, long failures24h, long denied24h, long changes7d,
                                List<AuditDto> recentSecurity) {
    }

    public record PrivilegeDef(String code, String kind, String group, String label, String description,
                               List<String> defaults, List<String> locked) {
    }

    public record PrivilegeMatrix(List<String> roles, List<PrivilegeDef> privileges, Map<String, List<String>> grants,
                                  Instant updatedAt, String updatedBy, long customSettings) {
    }

    public record PrivilegeUpdate(@jakarta.validation.constraints.NotNull Map<String, List<String>> grants) {
    }
}
