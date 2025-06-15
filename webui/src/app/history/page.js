'use client';
import { useState, useEffect } from 'react';

export default function History() {
  const [items, setItems] = useState([]);
  useEffect(() => {
    fetch('/history')
      .then(res => res.json())
      .then(data => setItems(data.history || []));
  }, []);

  const statusColor = status => {
    if (status === 'accepted') return 'bg-green-200';
    if (status === 'rejected') return 'bg-red-200';
    return 'bg-gray-200';
  };

  return (
    <div className="max-w-2xl mx-auto">
      <h1 className="text-xl font-bold mb-4">Historie</h1>
      <table className="w-full border-collapse">
        <thead>
          <tr>
            <th className="border p-2">Parameter</th>
            <th className="border p-2">Wert</th>
            <th className="border p-2">Ausgang</th>
          </tr>
        </thead>
        <tbody>
          {items.map(it => (
            <tr key={it.id} className={statusColor(it.status)}>
              <td className="border p-2">{it.name}</td>
              <td className="border p-2">{it.value}</td>
              <td className="border p-2">{it.status}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
