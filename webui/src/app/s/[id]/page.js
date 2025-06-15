'use client';
import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';

export default function SuggestDetail() {
  const params = useParams();
  const [suggestion, setSuggestion] = useState(null);
  const [comments, setComments] = useState([]);
  const [text, setText] = useState('');

  const reload = useCallback(() => {
    fetch(`/suggestions/${params.id}`)
      .then(r => r.json())
      .then(d => setSuggestion(d));
    fetch(`/suggestion-comments?id=${params.id}`)
      .then(r => r.json())
      .then(d => setComments(d.comments || []));
  }, [params.id]);

  useEffect(() => {
    reload();
  }, [reload]);

  const submit = async () => {
    await fetch('/suggestion-comments', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id: params.id, text }),
    });
    setText('');
    reload();
  };

  if (!suggestion) return <div>Lade...</div>;

  return (
    <div className="max-w-xl mx-auto space-y-4">
      <h1 className="text-xl font-bold">Vorschlag</h1>
      <div className="border p-4 rounded">
        <div>{suggestion.text}</div>
        <div className="text-sm text-gray-600">Zielwert: {suggestion.value}</div>
      </div>
      <h2 className="font-semibold">Kommentare</h2>
      <ul className="space-y-2">
        {comments.map(c => (
          <li key={c.uuid} className="border p-2 rounded">
            <div className="text-sm text-gray-600">{c.time}</div>
            <div>{c.text}</div>
          </li>
        ))}
      </ul>
      <textarea
        className="border p-2 w-full"
        value={text}
        onChange={e => setText(e.target.value)}
      />
      <button className="bg-blue-600 text-white px-4 py-2" onClick={submit}>
        Kommentar senden
      </button>
    </div>
  );
}
