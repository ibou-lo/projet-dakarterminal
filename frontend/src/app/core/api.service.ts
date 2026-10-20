import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  ActionProposal, AgentActivity, ChatReply, GatePassageDto, GateResult, Incoherence, Indicators, MovementDto,
  KeyStatus, MovementStats, PrivilegeMatrix, TokenResponse, AdminOverview, AdminUser, AuditEntry, UserForm, UserWithPassword, ZoneCreate, ZoneInfo, NotificationDto, PlaceLabel, TagDto, ParkOverview, Passport, PickupColumn, TaskDto, TicketDto, VehicleSummary,
} from './models';

/** Accès typé à l'API Dakar AutoTrack. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private http = inject(HttpClient);

  // Parc
  park(source?: string): Observable<ParkOverview> {
    const params = source ? new HttpParams().set('source', source) : undefined;
    return this.http.get<ParkOverview>('/api/park', { params });
  }
  incoherences(): Observable<Incoherence[]> {
    return this.http.get<Incoherence[]>('/api/park/incoherences');
  }
  notifications(): Observable<NotificationDto[]> {
    return this.http.get<NotificationDto[]>('/api/park/notifications');
  }
  zones(): Observable<ZoneInfo[]> {
    return this.http.get<ZoneInfo[]>('/api/zones');
  }
  createZone(z: ZoneCreate): Observable<ZoneInfo> {
    return this.http.post<ZoneInfo>('/api/zones', z);
  }
  updateZone(code: string, label: string, usage: string | null): Observable<ZoneInfo> {
    return this.http.put<ZoneInfo>(`/api/zones/${encodeURIComponent(code)}`, { label, usage });
  }
  addAllees(code: string, count: number, placesParAllee: number): Observable<ZoneInfo> {
    return this.http.post<ZoneInfo>(`/api/zones/${encodeURIComponent(code)}/allees`, { count, placesParAllee });
  }
  deleteZone(code: string): Observable<unknown> {
    return this.http.delete(`/api/zones/${encodeURIComponent(code)}`);
  }
  zoneRead(epc: string, zone: string): Observable<unknown> {
    return this.http.post('/api/park/zone-read', { epc, zone });
  }

  // Véhicules
  vehicles(filter: { status?: string; zone?: string; q?: string }): Observable<VehicleSummary[]> {
    let params = new HttpParams();
    Object.entries(filter).forEach(([k, v]) => { if (v) params = params.set(k, v); });
    return this.http.get<VehicleSummary[]>('/api/vehicles', { params });
  }
  counts(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>('/api/vehicles/counts');
  }
  passport(vin: string): Observable<Passport> {
    return this.http.get<Passport>(`/api/vehicles/${encodeURIComponent(vin)}`);
  }
  assign(vin: string, placeCode: string, confirmed: boolean): Observable<Passport> {
    return this.http.post<Passport>(`/api/vehicles/${encodeURIComponent(vin)}/assign`, { placeCode, confirmed });
  }
  receive(r: { vin: string; model?: string; color?: string; modelYear?: number | null; escale?: string; blNumber?: string;
    beneficiaryName?: string; beneficiaryIdSuffix?: string; forwarder?: string }): Observable<Passport> {
    return this.http.post<Passport>('/api/vehicles', r);
  }
  vinCheck(rawText: string): Observable<{ vin: string; validFormat: boolean; checkDigitOk: boolean; uncertainPositions: number[]; messages: string[]; alreadyKnown: boolean }> {
    return this.http.post<{ vin: string; validFormat: boolean; checkDigitOk: boolean; uncertainPositions: number[]; messages: string[]; alreadyKnown: boolean }>('/api/vehicles/vin-check', { rawText });
  }
  suggestPlace(zone: string): Observable<{ placeCode: string; placeLabel: string; qrPayload: string; freePlacesInZone: number }> {
    return this.http.get<{ placeCode: string; placeLabel: string; qrPayload: string; freePlacesInZone: number }>('/api/park/suggest-place', { params: { zone } });
  }
  receiveFormality(vin: string, type: string): Observable<Passport> {
    return this.http.post<Passport>(`/api/vehicles/${vin}/formalities/${type}/receive`, { source: 'Plateforme de formalités (simulation)' });
  }
  resetFormality(vin: string, type: string): Observable<Passport> {
    return this.http.post<Passport>(`/api/vehicles/${vin}/formalities/${type}/reset`, {});
  }
  issueTicket(vin: string, slot?: string): Observable<TicketDto> {
    return this.http.post<TicketDto>(`/api/vehicles/${vin}/ticket`, { slot: slot ?? null });
  }
  remind(vin: string): Observable<unknown> {
    return this.http.post(`/api/vehicles/${vin}/remind`, {});
  }

  // Mouvements
  movements(hours = 24): Observable<MovementDto[]> {
    return this.http.get<MovementDto[]>('/api/movements', { params: { hours } });
  }
  movementStats(): Observable<MovementStats> {
    return this.http.get<MovementStats>('/api/movements/stats');
  }
  missingArrivals(): Observable<MovementDto[]> {
    return this.http.get<MovementDto[]>('/api/movements/missing-arrivals');
  }
  agents(): Observable<AgentActivity[]> {
    return this.http.get<AgentActivity[]>('/api/movements/agents');
  }
  createTask(vin: string, placeCode: string | null, reason: string): Observable<TaskDto> {
    return this.http.post<TaskDto>('/api/tasks', { vin, placeCode, reason });
  }
  tasks(): Observable<TaskDto[]> {
    return this.http.get<TaskDto[]>('/api/tasks');
  }

  // Tickets de retrait
  tickets(state?: string): Observable<TicketDto[]> {
    const params = state ? new HttpParams().set('state', state) : undefined;
    return this.http.get<TicketDto[]>('/api/tickets', { params });
  }
  ticket(number: string): Observable<TicketDto> {
    return this.http.get<TicketDto>(`/api/tickets/${encodeURIComponent(number)}`);
  }

  // Innovations : étiquettes hybrides QR + RFID, armoire à clés
  tags(zone?: string, q?: string): Observable<TagDto[]> {
    let params = new HttpParams();
    if (zone) params = params.set('zone', zone);
    if (q) params = params.set('q', q);
    return this.http.get<TagDto[]>('/api/tags', { params });
  }
  associateTag(vin: string, epc: string, reusable: boolean): Observable<TagDto> {
    return this.http.post<TagDto>(`/api/vehicles/${encodeURIComponent(vin)}/tag`, { epc, reusable });
  }
  placeLabels(zone: string, allee?: number | null): Observable<PlaceLabel[]> {
    let params = new HttpParams().set('zone', zone);
    if (allee) params = params.set('allee', allee);
    return this.http.get<PlaceLabel[]>('/api/labels/places', { params });
  }
  keys(state?: string): Observable<KeyStatus[]> {
    const params = state ? new HttpParams().set('state', state) : undefined;
    return this.http.get<KeyStatus[]>('/api/keys', { params });
  }
  keyAction(vin: string, action: 'DEPOSEE' | 'SORTIE' | 'REMISE_CLIENT', holder?: string, slot?: string): Observable<KeyStatus> {
    return this.http.post<KeyStatus>(`/api/keys/${encodeURIComponent(vin)}`, { action, holder: holder ?? null, slot: slot ?? null });
  }

  // Enlèvements
  board(): Observable<PickupColumn[]> {
    return this.http.get<PickupColumn[]>('/api/pickups/board');
  }

  // Portail
  gateScan(body: { ticketQr?: string; rfidEpc?: string; vin?: string; lane?: number }): Observable<GateResult> {
    return this.http.post<GateResult>('/api/gate/scan', body);
  }
  gateDecision(id: number, decision: 'EXIT_VALIDATED' | 'BLOCKED', reason?: string): Observable<GatePassageDto> {
    return this.http.post<GatePassageDto>(`/api/gate/passages/${id}/decision`, { decision, reason });
  }
  gatePassages(): Observable<GatePassageDto[]> {
    return this.http.get<GatePassageDto[]>('/api/gate/passages');
  }
  gateStats(): Observable<Record<string, number>> {
    return this.http.get<Record<string, number>>('/api/gate/stats');
  }

  // Indicateurs
  indicators(): Observable<Indicators> {
    return this.http.get<Indicators>('/api/indicators');
  }

  // Assistant
  chat(message: string, contextVin?: string | null): Observable<ChatReply> {
    return this.http.post<ChatReply>('/api/assistant/chat', { message, contextVin: contextVin ?? null });
  }
  decideAction(id: number, accept: boolean): Observable<ActionProposal> {
    return this.http.post<ActionProposal>(`/api/assistant/actions/${id}`, { accept });
  }

  // Administration
  adminOverview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>('/api/admin/overview');
  }
  adminUsers(): Observable<AdminUser[]> {
    return this.http.get<AdminUser[]>('/api/admin/users');
  }
  createUser(u: UserForm): Observable<UserWithPassword> {
    return this.http.post<UserWithPassword>('/api/admin/users', u);
  }
  updateUser(username: string, u: UserForm): Observable<AdminUser> {
    return this.http.put<AdminUser>(`/api/admin/users/${encodeURIComponent(username)}`, u);
  }
  userAction(username: string, action: 'enable' | 'disable' | 'unlock'): Observable<AdminUser> {
    return this.http.post<AdminUser>(`/api/admin/users/${encodeURIComponent(username)}/${action}`, {});
  }
  resetPassword(username: string): Observable<UserWithPassword> {
    return this.http.post<UserWithPassword>(`/api/admin/users/${encodeURIComponent(username)}/reset-password`, {});
  }
  audit(f: { category?: string; action?: string; actor?: string; q?: string; days?: number; limit?: number }): Observable<AuditEntry[]> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(f)) if (v !== undefined && v !== null && v !== '') params = params.set(k, String(v));
    return this.http.get<AuditEntry[]>('/api/admin/audit', { params });
  }
  changePassword(currentPassword: string, newPassword: string): Observable<TokenResponse> {
    return this.http.post<TokenResponse>('/api/auth/password', { currentPassword, newPassword });
  }
  privileges(): Observable<PrivilegeMatrix> {
    return this.http.get<PrivilegeMatrix>('/api/admin/privileges');
  }
  savePrivileges(grants: Record<string, string[]>): Observable<PrivilegeMatrix> {
    return this.http.put<PrivilegeMatrix>('/api/admin/privileges', { grants });
  }
  resetPrivileges(): Observable<PrivilegeMatrix> {
    return this.http.post<PrivilegeMatrix>('/api/admin/privileges/reset', {});
  }
}
