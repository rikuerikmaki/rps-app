import { useMemo, useState } from 'react';

import {
  ApiError,
  GameSessionResponse,
  RpsApiClient,
  RpsItem,
  completionPayloadFor,
  roundWinner,
} from './api';

const items: RpsItem[] = ['rock', 'paper', 'scissors'];
const labels: Record<RpsItem, string> = {
  rock: 'Rock',
  paper: 'Paper',
  scissors: 'Scissors',
};

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api';

type MessageKind = 'idle' | 'loading' | 'success' | 'error';

export function App() {
  const api = useMemo(() => new RpsApiClient(apiBaseUrl), []);
  const [choice, setChoice] = useState<RpsItem>('rock');
  const [session, setSession] = useState<GameSessionResponse | null>(null);
  const [message, setMessage] = useState('Choose an item and start a session.');
  const [messageKind, setMessageKind] = useState<MessageKind>('idle');
  const [loading, setLoading] = useState(false);
  const [roundSummary, setRoundSummary] = useState<string | null>(null);

  async function startSession() {
    setLoading(true);
    setMessageKind('loading');
    setMessage(`Starting session with ${labels[choice]}...`);
    setRoundSummary(null);
    try {
      const created = await api.startSession(choice);
      setSession(created);
      setMessageKind('success');
      setMessage(`Session ${created.id} started.`);
    } catch (error) {
      setMessageKind('error');
      setMessage(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  async function playRound(opponentChoice: RpsItem) {
    if (session == null) return;
    const winner = roundWinner(session.playerChoice, opponentChoice);

    if (winner == null) {
      setMessageKind('success');
      setRoundSummary(
        `${labels[session.playerChoice]} tied ${labels[opponentChoice]}. Pick another opponent move.`,
      );
      setMessage('Round tied. The backend session is still open.');
      return;
    }

    setLoading(true);
    setMessageKind('loading');
    setRoundSummary(
      `${labels[session.playerChoice]} vs ${labels[opponentChoice]}: ${labels[winner]} wins.`,
    );
    setMessage(`Saving ${labels[winner]} as winner...`);
    try {
      const completed = await api.completeSession(session.id, completionPayloadFor(winner));
      setSession(completed);
      setMessageKind('success');
      setMessage(`${labels[winner]} wins. Session saved.`);
    } catch (error) {
      setMessageKind('error');
      setMessage(errorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="shell">
      <section className="panel">
        <div className="eyebrow">First frontend slice</div>
        <h1>Rock Paper Scissors Battle</h1>
        <p className="lede">
          Start a backend session, then play one round and save the winner.
        </p>
        <p className="apiUrl">API: {apiBaseUrl}</p>

        <div className="choiceGrid" aria-label="Choose rock, paper, or scissors">
          {items.map((item) => (
            <button
              className={choice === item ? 'choice active' : 'choice'}
              disabled={loading}
              key={item}
              onClick={() => setChoice(item)}
              type="button"
            >
              <span>{labels[item]}</span>
              <small>{ruleText(item)}</small>
            </button>
          ))}
        </div>

        <div className="actions">
          <button className="primary" disabled={loading} onClick={startSession} type="button">
            {session == null ? 'Start session' : 'Start new session'}
          </button>
        </div>

        <div className={`status ${messageKind}`} role="status">
          {message}
        </div>
      </section>

      <section className="panel sessionPanel">
        <h2>Session</h2>
        {session == null ? (
          <p className="empty">No session has been started yet.</p>
        ) : (
          <>
            <dl className="details">
              <div>
                <dt>ID</dt>
                <dd>{session.id}</dd>
              </div>
              <div>
                <dt>Player choice</dt>
                <dd>{labels[session.playerChoice]}</dd>
              </div>
              <div>
                <dt>Status</dt>
                <dd>{session.status}</dd>
              </div>
              <div>
                <dt>Winner</dt>
                <dd>{session.winner == null ? 'Pending' : labels[session.winner]}</dd>
              </div>
            </dl>

            {roundSummary == null ? null : <p className="roundSummary">{roundSummary}</p>}

            <div className="resultActions">
              <span>Play opponent move</span>
              {items.map((item) => (
                <button
                  disabled={loading || session.status === 'completed'}
                  key={item}
                  onClick={() => playRound(item)}
                  type="button"
                >
                  {labels[item]}
                </button>
              ))}
            </div>
          </>
        )}
      </section>
    </main>
  );
}

function ruleText(item: RpsItem): string {
  if (item === 'rock') return 'beats scissors';
  if (item === 'paper') return 'beats rock';
  return 'beats paper';
}

function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return 'Unexpected frontend error.';
}
