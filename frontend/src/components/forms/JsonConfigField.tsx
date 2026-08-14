interface JsonConfigFieldProps {
  id: string;
  label: string;
  hint: string;
  value: string;
  onChange: (value: string) => void;
  error?: string | null;
}

export function parseJsonObject(text: string): { ok: true; value: unknown } | { ok: false; error: string } {
  try {
    const value: unknown = JSON.parse(text);
    if (typeof value !== "object" || value === null) {
      return { ok: false, error: "JSON must be an object or array." };
    }
    return { ok: true, value };
  } catch {
    return { ok: false, error: "Invalid JSON." };
  }
}

export async function readJsonFile(file: File): Promise<string> {
  const text = await file.text();
  const parsed = parseJsonObject(text);
  if (!parsed.ok) throw new Error(parsed.error);
  return JSON.stringify(parsed.value, null, 2);
}

export function JsonConfigField({ id, label, hint, value, onChange, error }: JsonConfigFieldProps) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <p className="panelsub" style={{ marginBottom: 8 }}>
        {hint}
      </p>
      <textarea
        id={id}
        className="jsonbox"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        spellCheck={false}
      />
      {error ? <div className="formError">{error}</div> : null}
    </div>
  );
}
