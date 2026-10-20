// Modèles échangés avec l'API Spring Boot (voir backend web/dto/Dtos.java).

export interface UserInfo {
  username: string;
  fullName: string;
  role: 'ADMIN' | 'SUPERVISEUR' | 'AGENT' | 'PORTAIL' | 'CLIENT';
  zone?: string | null;
  bl?: string | null;
  /** Privilèges effectifs du profil (configurés par l'administrateur). */
  privileges?: string[];
  /** Mot de passe provisoire : changement obligatoire avant tout accès. */
  mustChangePassword?: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserInfo;
}

export interface LastScan { at: string; by: string | null; source: string; }

export type VehicleStatus = 'AWAITING_AUTHORIZATION' | 'READY' | 'POSITION_TO_CONFIRM' | 'EXITED';

export interface VehicleSummary {
  vin: string;
  shortCode: string;
  model: string;
  color: string;
  modelYear: number;
  escale: string;
  blNumber: string;
  placeCode: string | null;
  placeLabel: string;
  zone: string | null;
  status: VehicleStatus;
  statusLabel: string;
  stage: string;
  formalitiesDone: number;
  formalitiesTotal: number;
  reservesCount: number;
  daysOnPark: number;
  lastScan: LastScan | null;
}

export interface FormalityDto { type: string; label: string; status: 'PENDING' | 'RECEIVED'; receivedAt: string | null; reference: string | null; source: string | null; }
export interface PhotoDto { view: string; storageRef: string; }
export interface ReserveDto { location: string; description: string; photoView: string; }
export interface InspectionDto { fuelLevel: string; keysCount: number; mileage: number | null; photos: PhotoDto[]; reserves: ReserveDto[]; inspectedBy: string; inspectedAt: string; }
export interface HistoryItem { at: string; kind: string; what: string; who: string | null; }

export interface TicketDto {
  ticketNumber: string; vin: string; shortCode: string; model: string; placeLabel: string;
  beneficiary: string; issuedAt: string; validUntil: string; slot: string | null; used: boolean; qrToken: string;
}

export interface Passport {
  vehicle: VehicleSummary;
  beneficiaryMasked: string;
  forwarder: string;
  rfidEpc: string;
  formalities: FormalityDto[];
  inspection: InspectionDto | null;
  history: HistoryItem[];
  ticket: TicketDto | null;
  activeAlerts: string[];
}

export interface PlaceCell {
  code: string; allee: number; numero: number; state: 'FREE' | 'OCCUPIED' | 'READY';
  vin: string | null; shortCode: string | null; lastReadAt: string | null; lastReadSource: string | null;
  freshness: 'FRESH_1H' | 'H1_6' | 'H6_24' | 'OLD' | 'NEVER'; incoherent: boolean;
}

export interface ZoneMap { zone: string; label: string; total: number; occupied: number; ready: number; freshPct: number; allees: PlaceCell[][]; }
export interface ParkOverview { vehiclesOnPark: number; confirmedPositionsPct: number; readyToPickUp: number; movements24h: number; zones: ZoneMap[]; }
export interface Incoherence { kind: string; vin: string; placeCode: string; message: string; at: string; }

export interface MovementDto { id: number; startedAt: string; vin: string; shortCode: string; type: string; fromPlace: string | null; toPlace: string | null; agent: string | null; traceComplete: boolean; arrivalScannedAt: string | null; }
export interface MovementStats { today: number; traceCompletePct: number; missingArrivals: number; avgMoveMinutes: number | null; perHour: Record<string, number>; }
export interface AgentActivity { agent: string; movements: number; }
export interface TaskDto { id: number; vin: string; placeCode: string; reason: string; assignedTo: string; status: string; createdAt: string; }

export interface PickupCard { vin: string; shortCode: string; model: string; placeLabel: string; client: string; missing: string[]; meta: string; canIssueTicket: boolean; canRemind: boolean; }
export interface PickupColumn { stage: string; title: string; hint: string; count: number; cards: PickupCard[]; }

export interface GateCheck { code: string; label: string; note: string; result: 'OK' | 'KO' | 'MANUAL' | 'INFO'; }
export interface GateResult { passageId: number; vin: string; ticketNumber: string | null; decision: 'PENDING_AGENT' | 'EXIT_VALIDATED' | 'BLOCKED'; title: string; subtitle: string; checks: GateCheck[]; }
export interface GatePassageDto { id: number; readAt: string; vin: string; ticketNumber: string | null; lane: number; decision: string; reason: string | null; decidedBy: string | null; }

export interface IndicatorSeries { code: string; title: string; unit: string; reference: number; current: number; delta: string; lowerIsBetter: boolean; weeks: number[]; method: string; }
export interface Indicators { fictitious: boolean; series: IndicatorSeries[]; }

export interface ActionProposal { id: number; type: string; vin: string; description: string; status: 'PROPOSED' | 'CONFIRMED' | 'CANCELLED'; }
export interface ChatTable { headers: string[]; rows: string[][]; }
export interface ChatReply { text: string; vehicle: VehicleSummary | null; table: ChatTable | null; action: ActionProposal | null; sources: string[]; suggestions: string[]; }

export interface NotificationDto { at: string; channel: string; to: string; message: string; }

// ---------- Innovations QR & RFID ----------
export interface TagDto { vin: string; shortCode: string; model: string; placeLabel: string; epc: string | null; reusable: boolean; vehicleQr: string; keyQr: string; }
export interface PlaceLabel { code: string; label: string; qrPayload: string; }
export interface KeyStatus { vin: string; shortCode: string; model: string; placeLabel: string; state: 'ARMOIRE' | 'SORTIE' | 'REMISE' | 'INCONNU'; holder: string | null; slot: string | null; since: string | null; keyQr: string; }

export interface ZoneInfo { code: string; label: string; usage: string | null; allees: number; places: number; occupied: number; deletable: boolean; createdBy: string | null; createdAt: string | null; }
export interface ZoneCreate { code: string; label: string; usage: string; allees: number; placesParAllee: number; }

export interface AdminUser { username: string; fullName: string; role: string; zone: string | null; email: string | null; phone: string | null; enabled: boolean; locked: boolean; failedAttempts: number; lockedUntil: string | null; createdAt: string | null; createdBy: string | null; lastLoginAt: string | null; passwordChangedAt: string | null; }
export interface UserWithPassword { user: AdminUser; temporaryPassword: string; }
export interface UserForm { username?: string; fullName: string; role: string; zone: string | null; email: string | null; phone: string | null; }
export interface AuditEntry { id: number; at: string; actor: string; actorName: string | null; actorRole: string | null; category: string; action: string; label: string; target: string | null; details: string | null; ip: string | null; success: boolean; }
export interface AdminOverview { users: number; activeUsers: number; lockedUsers: number; usersByRole: Record<string, number>; logins24h: number; failures24h: number; denied24h: number; changes7d: number; recentSecurity: AuditEntry[]; }

export interface PrivilegeDef { code: string; kind: 'PAGE' | 'ACTION'; group: string; label: string; description: string; defaults: string[]; locked: string[]; }
export interface PrivilegeMatrix { roles: string[]; privileges: PrivilegeDef[]; grants: Record<string, string[]>; updatedAt: string | null; updatedBy: string | null; customSettings: number; }
