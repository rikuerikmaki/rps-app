import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, RpsApiClient, completionPayloadFor, defaultFetch, roundWinner } from './api';

const originalFetch = globalThis.fetch;

afterEach(() => {
  globalThis.fetch = originalFetch;
});

describe('RpsApiClient', () => {
  it('starts a session with the documented payload', async () => {
    const fetchCalls: Array<[RequestInfo | URL, RequestInit | undefined]> = [];
    const client = new RpsApiClient('http://localhost:8080/api', async (input, init) => {
      fetchCalls.push([input, init]);
      return jsonResponse({
        id: '4d93c354-d7c5-43cf-9be2-b7756117d4f2',
        playerChoice: 'rock',
        status: 'started',
        winner: null,
        initialRockCount: 10,
        initialPaperCount: 10,
        initialScissorsCount: 10,
        finalRockCount: null,
        finalPaperCount: null,
        finalScissorsCount: null,
        transformations: null,
        durationMs: null,
        createdAt: '2026-06-11T12:15:30.123456+03:00',
        completedAt: null,
      });
    });

    const session = await client.startSession('rock');

    expect(session.playerChoice).toBe('rock');
    expect(fetchCalls[0][0]).toBe('http://localhost:8080/api/sessions');
    expect(fetchCalls[0][1]).toMatchObject({
      method: 'POST',
      body: JSON.stringify({ playerChoice: 'rock' }),
    });
  });

  it('builds a valid completion payload for a winner', () => {
    expect(completionPayloadFor('paper')).toEqual({
      winner: 'paper',
      finalRockCount: 0,
      finalPaperCount: 30,
      finalScissorsCount: 0,
      transformations: 0,
      durationMs: 0,
    });
  });

  it('calls global fetch without losing its receiver binding', async () => {
    const fetchMock = vi.fn(function (this: typeof globalThis) {
      if (this !== globalThis) {
        throw new Error('Illegal invocation');
      }
      return Promise.resolve(jsonResponse({ status: 'ok', database: 'ok' }));
    });
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    await expect(defaultFetch('http://localhost:8080/api/health')).resolves.toBeInstanceOf(
      Response,
    );
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('calculates rock paper scissors round winners', () => {
    expect(roundWinner('rock', 'scissors')).toBe('rock');
    expect(roundWinner('paper', 'rock')).toBe('paper');
    expect(roundWinner('scissors', 'paper')).toBe('scissors');
    expect(roundWinner('rock', 'paper')).toBe('paper');
    expect(roundWinner('rock', 'rock')).toBeNull();
  });

  it('throws ApiError with backend error payloads', async () => {
    const client = new RpsApiClient('http://localhost:8080/api', async () =>
      jsonResponse({ error: 'Session not found.' }, 404),
    );

    await expect(client.getSession('missing')).rejects.toMatchObject({
      name: 'ApiError',
      status: 404,
      payload: { error: 'Session not found.' },
    });
  });
});

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
