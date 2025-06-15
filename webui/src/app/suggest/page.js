'use client';
import { useState, useEffect } from 'react';

export default function Suggest() {
  const [params, setParams] = useState([]);
  const [rules, setRules] = useState([]);
  const [paramId, setParamId] = useState('');
  const [value, setValue] = useState('');
  const [reason, setReason] = useState('');

  useEffect(() => {
    fetch('/configs')
      .then(res => res.json())
      .then(data => setParams(data.parameters || []));
    fetch('/rules')
      .then(res => res.json())
      .then(data => setRules(data.rules || []));
  }, []);

  const submit = async () => {
    await fetch('/submit-suggestion', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ parameter: paramId, value, reason }),
    });
    setValue('');
    setReason('');
    alert('Vorschlag gesendet');
  };

  return (
    <div className="max-w-xl mx-auto space-y-4">
      <h1 className="text-xl font-bold">Vorschlag einreichen</h1>
      <div>
        <label className="block mb-1">Parameter</label>
        <select
          className="border p-2 w-full"
          value={paramId}
          onChange={e => setParamId(e.target.value)}
        >
          <option value="">Bitte wählen</option>
          {params.map(p => (
            <option key={p.id} value={p.id}>
              {p.name}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label className="block mb-1">Zielwert</label>
        <input
          className="border p-2 w-full"
          value={value}
          onChange={e => setValue(e.target.value)}
        />
      </div>
      <div>
        <label className="block mb-1">Begründung</label>
        <textarea
          className="border p-2 w-full"
          value={reason}
          onChange={e => setReason(e.target.value)}
        />
      </div>
      <button className="bg-blue-600 text-white px-4 py-2" onClick={submit}>
        Abschicken
      </button>
      <h2 className="text-lg font-semibold mt-6">Regeln</h2>
      <ul className="list-disc pl-4 space-y-1">
        {rules.map(r => (
          <li key={r.id}>{r.text}</li>
        ))}
      </ul>
    </div>
  );
}
