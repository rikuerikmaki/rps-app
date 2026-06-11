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
type BattleView = {
  playerChoice: RpsItem;
  opponentChoice: RpsItem;
  winner: RpsItem | null;
};

export function App() {
  const api = useMemo(() => new RpsApiClient(apiBaseUrl), []);
  const [choice, setChoice] = useState<RpsItem>('rock');
  const [session, setSession] = useState<GameSessionResponse | null>(null);
  const [message, setMessage] = useState('Choose an item and start a session.');
  const [messageKind, setMessageKind] = useState<MessageKind>('idle');
  const [loading, setLoading] = useState(false);
  const [roundSummary, setRoundSummary] = useState<string | null>(null);
  const [battle, setBattle] = useState<BattleView | null>(null);

  async function startSession() {
    setLoading(true);
    setMessageKind('loading');
    setMessage(`Starting session with ${labels[choice]}...`);
    setRoundSummary(null);
    setBattle(null);
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

    setBattle({
      playerChoice: session.playerChoice,
      opponentChoice,
      winner,
    });

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
      await delay(1700);
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
              <Character item={item} />
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

        {battle == null ? null : <BattleScene battle={battle} />}

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

function BattleScene({ battle }: { battle: BattleView }) {
  const resultText =
    battle.winner == null
      ? 'Tie. Neither side falls.'
      : `${labels[battle.winner]} defeats ${
          battle.winner === battle.playerChoice
            ? labels[battle.opponentChoice]
            : labels[battle.playerChoice]
        }.`;

  return (
    <div className="battleScene" aria-label="Rock paper scissors battleground">
      <div className="battleGround">
        <BattleToken
          item={battle.playerChoice}
          label="You"
          side="left"
          outcome={battleOutcome(battle.playerChoice, battle.winner)}
        />
        <div className="impact">VS</div>
        <BattleToken
          item={battle.opponentChoice}
          label="Opponent"
          side="right"
          outcome={battleOutcome(battle.opponentChoice, battle.winner)}
        />
      </div>
      <p className="battleResult">{resultText}</p>
    </div>
  );
}

function BattleToken({
  item,
  label,
  side,
  outcome,
}: {
  item: RpsItem;
  label: string;
  side: 'left' | 'right';
  outcome: 'winner' | 'loser' | 'tie';
}) {
  return (
    <div className={`battleToken ${item} ${side} ${outcome}`}>
      <Character item={item} compact />
      <strong>{labels[item]}</strong>
      <small>{label}</small>
    </div>
  );
}

function Character({ item, compact = false }: { item: RpsItem; compact?: boolean }) {
  const className = `character ${compact ? 'compact ' : ''}${item}Character`;

  if (item === 'rock') {
    return (
      <span className={className} aria-hidden="true">
        <span className="rockBody">
          <span className="face eye left" />
          <span className="face eye right" />
          <span className="face mouth" />
        </span>
      </span>
    );
  }

  if (item === 'paper') {
    return (
      <span className={className} aria-hidden="true">
        <span className="paperBody">
          <span className="paperFold" />
          <span className="face eye left" />
          <span className="face eye right" />
          <span className="face mouth" />
        </span>
      </span>
    );
  }

  return (
    <span className={className} aria-hidden="true">
      <span className="scissorBlade bladeLeft" />
      <span className="scissorBlade bladeRight" />
      <span className="scissorHandle handleLeft" />
      <span className="scissorHandle handleRight" />
      <span className="scissorPivot" />
      <span className="face eye left" />
      <span className="face eye right" />
    </span>
  );
}

function battleOutcome(item: RpsItem, winner: RpsItem | null): 'winner' | 'loser' | 'tie' {
  if (winner == null) return 'tie';
  return item === winner ? 'winner' : 'loser';
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

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}
