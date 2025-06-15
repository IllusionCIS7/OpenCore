'use client';
import { useState, useEffect } from 'react';

export default function WebAdmin() {
  const [params, setParams] = useState([]);
  const [policies, setPolicies] = useState([]);

  const load = () => {
    fetch('/admin/config-parameter')
      .then(r => r.json())
      .then(d => setParams(d.parameters || []));
    fetch('/admin/policies')
      .then(r => r.json())
      .then(d => setPolicies(d.policies || []));
  };

  useEffect(() => {
    load();
  }, []);

  const saveParam = async (id, value) => {
    await fetch('/admin/config-parameter', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, value }),
    });
    load();
  };

  return (
    <div className="space-y-6 max-w-3xl mx-auto">
      <h1 className="text-xl font-bold">Adminbereich</h1>

      <section>
        <h2 className="font-semibold mb-2">Parameter</h2>
        <table className="w-full border-collapse">
          <thead>
            <tr>
              <th className="border p-2">Name</th>
              <th className="border p-2">Wert</th>
              <th className="border p-2"></th>
            </tr>
          </thead>
          <tbody>
            {params.map(p => (
              <tr key={p.id}>
                <td className="border p-2">{p.name}</td>
                <td className="border p-2">
                  <input
                    defaultValue={p.value}
                    className="border p-1 w-full"
                    onBlur={e => saveParam(p.id, e.target.value)}
                  />
                </td>
                <td className="border p-2"></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section>
        <h2 className="font-semibold mb-2">GPT-Regeln</h2>
        <ul className="space-y-2">
          {policies.map(pol => (
            <li key={pol.id} className="border p-2 rounded">
              {pol.text}
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
