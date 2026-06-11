export type RpsItem = 'rock' | 'paper' | 'scissors';

export type SessionStatus = 'started' | 'completed';

export interface HealthResponse {
  status: 'ok';
  database: 'ok' | 'unavailable';
}

export interface RulesResponse {
  items: RpsItem[];
  rules: Record<RpsItem, RpsItem>;
}

export interface GameSessionResponse {
  id: string;
  playerChoice: RpsItem;
  status: SessionStatus;
  winner: RpsItem | null;
  initialRockCount: number;
  initialPaperCount: number;
  initialScissorsCount: number;
  finalRockCount: number | null;
  finalPaperCount: number | null;
  finalScissorsCount: number | null;
  transformations: number | null;
  durationMs: number | null;
  createdAt: string;
  completedAt: string | null;
}

export interface StartSessionRequest {
  playerChoice: RpsItem;
}

export interface CompleteSessionRequest {
  winner: RpsItem;
  finalRockCount: number;
  finalPaperCount: number;
  finalScissorsCount: number;
  transformations: number;
  durationMs: number;
}

export interface ErrorResponse {
  error: string;
}

type FetchLike = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;

export const defaultFetch: FetchLike = (input, init) => {
  if (typeof window !== 'undefined') {
    return window.fetch(input, init);
  }
  return globalThis.fetch(input, init);
};

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly payload?: ErrorResponse,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export class RpsApiClient {
  constructor(
    baseUrl: string,
    private readonly fetchFn: FetchLike = defaultFetch,
  ) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  private readonly baseUrl: string;

  async getHealth(): Promise<HealthResponse> {
    return this.request<HealthResponse>('/health');
  }

  async getRules(): Promise<RulesResponse> {
    return this.request<RulesResponse>('/rules');
  }

  async startSession(playerChoice: RpsItem): Promise<GameSessionResponse> {
    return this.request<GameSessionResponse>('/sessions', {
      method: 'POST',
      body: { playerChoice },
    });
  }

  async listSessions(limit?: number): Promise<GameSessionResponse[]> {
    const query = limit == null ? '' : `?limit=${encodeURIComponent(limit)}`;
    return this.request<GameSessionResponse[]>(`/sessions${query}`);
  }

  async getSession(id: string): Promise<GameSessionResponse> {
    return this.request<GameSessionResponse>(`/sessions/${encodeURIComponent(id)}`);
  }

  async completeSession(
    id: string,
    payload: CompleteSessionRequest,
  ): Promise<GameSessionResponse> {
    return this.request<GameSessionResponse>(`/sessions/${encodeURIComponent(id)}/complete`, {
      method: 'POST',
      body: payload,
    });
  }

  private async request<T>(
    path: string,
    options: { method?: 'GET' | 'POST'; body?: unknown } = {},
  ): Promise<T> {
    const response = await this.fetchFn(`${this.baseUrl}${path}`, {
      method: options.method ?? 'GET',
      headers: options.body == null ? undefined : { 'Content-Type': 'application/json' },
      body: options.body == null ? undefined : JSON.stringify(options.body),
    });

    const text = await response.text();
    const data = text.length > 0 ? JSON.parse(text) : undefined;

    if (!response.ok) {
      const payload = isErrorResponse(data) ? data : undefined;
      throw new ApiError(payload?.error ?? response.statusText, response.status, payload);
    }

    return data as T;
  }
}

export function completionPayloadFor(winner: RpsItem): CompleteSessionRequest {
  return {
    winner,
    finalRockCount: winner === 'rock' ? 30 : 0,
    finalPaperCount: winner === 'paper' ? 30 : 0,
    finalScissorsCount: winner === 'scissors' ? 30 : 0,
    transformations: 0,
    durationMs: 0,
  };
}

export function roundWinner(playerChoice: RpsItem, opponentChoice: RpsItem): RpsItem | null {
  if (playerChoice === opponentChoice) return null;
  if (playerChoice === 'rock' && opponentChoice === 'scissors') return playerChoice;
  if (playerChoice === 'scissors' && opponentChoice === 'paper') return playerChoice;
  if (playerChoice === 'paper' && opponentChoice === 'rock') return playerChoice;
  return opponentChoice;
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  return typeof value === 'object' && value !== null && 'error' in value;
}
