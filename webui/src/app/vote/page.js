'use client';
import { useState, useEffect } from 'react';

export default function Vote() {
  const [suggestions, setSuggestions] = useState([]);

  const load = () => {
    fetch('/suggestions')
      .then(res => res.json())
      .then(data => setSuggestions(data.suggestions || []));
  };

  useEffect(() => {
    load();
  }, []);

  const cast = async (id, vote) => {
    await fetch('/cast-vote', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ suggestion: id, vote }),
    });
    load();
  };

  return (
    <div className="space-y-4 max-w-2xl mx-auto">
      <h1 className="text-xl font-bold">Offene Vorschläge</h1>
      {suggestions.map(s => (
        <div key={s.id} className="border p-4 rounded">
          <div className="font-semibold">{s.text || s.name}</div>
          <div className="text-sm mb-2">Zielwert: {s.value}</div>
          <div className="flex gap-2">
            <button
              className="bg-green-600 text-white px-3 py-1"
              onClick={() => cast(s.id, 'yes')}
            >
              Ja
            </button>
            <button
              className="bg-red-600 text-white px-3 py-1"
              onClick={() => cast(s.id, 'no')}
            >
              Nein
            </button>
            <button
              className="bg-gray-300 px-3 py-1"
              onClick={() => cast(s.id, 'abstain')}
            >
              Enthaltung
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
